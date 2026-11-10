import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPoller, type PollerVisibility } from './directChatPoller';

function visibility(initial = true) {
    let visible = initial;
    const listeners = new Set<() => void>();
    const adapter: PollerVisibility = {
        isVisible: () => visible,
        subscribe: listener => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    };
    return { adapter, set(next: boolean) { visible = next; listeners.forEach(listener => listener()); }, listeners };
}

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); });

describe('폴링', () => {
    it('시작하면 바로 조회하고 주기마다 반복한다', async () => {
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(999);
        expect(run).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(run).toHaveBeenCalledTimes(2);
        poller.stop();
    });

    it('이전 요청이 끝나기 전에는 다음 요청을 보내지 않는다', async () => {
        const finishes: Array<() => void> = [];
        const run = vi.fn(() => new Promise<void>(resolve => { finishes.push(resolve); }));
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(10000);
        expect(run).toHaveBeenCalledTimes(1);
        finishes[0]();
        await vi.advanceTimersByTimeAsync(999);
        expect(run).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(run).toHaveBeenCalledTimes(2);
        poller.stop();
    });

    it('실패하면 간격을 두 배씩 최대값까지 늘리고 성공하면 되돌린다', async () => {
        let fail = true;
        const onFailure = vi.fn();
        const run = vi.fn(async () => { if (fail) throw new Error('실패'); });
        const poller = createPoller({ interval: 1000, maxInterval: 4000, run, onFailure, visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1999);
        expect(run).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(run).toHaveBeenCalledTimes(2);
        await vi.advanceTimersByTimeAsync(4000);
        expect(run).toHaveBeenCalledTimes(3);
        // 최대값에서 멈춘다. 2의 거듭제곱대로면 8초를 기다린다.
        await vi.advanceTimersByTimeAsync(4000);
        expect(run).toHaveBeenCalledTimes(4);
        expect(onFailure).toHaveBeenCalledTimes(4);
        fail = false;
        await vi.advanceTimersByTimeAsync(4000);
        expect(run).toHaveBeenCalledTimes(5);
        await vi.advanceTimersByTimeAsync(1000);
        expect(run).toHaveBeenCalledTimes(6);
        poller.stop();
    });

    it('탭이 숨겨지면 멈추고 돌아오면 바로 조회한다', async () => {
        const tab = visibility();
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: tab.adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(1);
        tab.set(false);
        await vi.advanceTimersByTimeAsync(60000);
        expect(run).toHaveBeenCalledTimes(1);
        tab.set(true);
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(2);
        await vi.advanceTimersByTimeAsync(1000);
        expect(run).toHaveBeenCalledTimes(3);
        poller.stop();
    });

    it('탭이 숨겨진 채로 시작하면 조회하지 않는다', async () => {
        const tab = visibility(false);
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: tab.adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(5000);
        expect(run).not.toHaveBeenCalled();
        poller.stop();
    });

    it('poke는 바로 조회하고, 요청 중이면 끝난 뒤 한 번 더 조회한다', async () => {
        const finishes: Array<() => void> = [];
        const run = vi.fn(() => new Promise<void>(resolve => { finishes.push(resolve); }));
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        poller.poke();
        poller.poke();
        expect(run).toHaveBeenCalledTimes(1);
        finishes[0]();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(2);
        finishes[1]();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(2);
        poller.stop();
    });

    it('멈추면 진행 중인 요청을 취소하고 다시 조회하지 않으며 구독을 해제한다', async () => {
        const tab = visibility();
        let signal: AbortSignal | undefined;
        const onFailure = vi.fn();
        const run = vi.fn((received: AbortSignal) => new Promise<void>((_resolve, reject) => {
            signal = received;
            received.addEventListener('abort', () => reject(new DOMException('취소', 'AbortError')));
        }));
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, onFailure, visibility: tab.adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        poller.stop();
        await vi.advanceTimersByTimeAsync(10000);
        expect(signal?.aborted).toBe(true);
        expect(run).toHaveBeenCalledTimes(1);
        expect(onFailure).not.toHaveBeenCalled();
        expect(tab.listeners.size).toBe(0);
        poller.poke();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(1);
    });

    it('실패 처리에서 멈추면 다음 조회를 예약하지 않는다', async () => {
        const run = vi.fn(async () => { throw new Error('만료'); });
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, onFailure: () => poller.stop(), visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(60000);
        expect(run).toHaveBeenCalledTimes(1);
    });
});

describe('실시간 연결과 함께 쓰는 폴링', () => {
    it('주기를 함수로 받으면 타이머를 걸 때마다 다시 읽고, reschedule은 바뀐 주기로 다시 건다', async () => {
        let base = 1000;
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: () => base, maxInterval: 60000, run, visibility: visibility().adapter });
        poller.start();
        await vi.advanceTimersByTimeAsync(0);
        base = 60000;
        await vi.advanceTimersByTimeAsync(1000);
        expect(run).toHaveBeenCalledTimes(2);
        await vi.advanceTimersByTimeAsync(59999);
        expect(run).toHaveBeenCalledTimes(2);
        base = 1000;
        poller.reschedule();
        await vi.advanceTimersByTimeAsync(999);
        expect(run).toHaveBeenCalledTimes(2);
        await vi.advanceTimersByTimeAsync(1);
        expect(run).toHaveBeenCalledTimes(3);
        poller.stop();
    });

    it('evenIfHidden은 숨겨진 탭에서도 한 번 조회하지만 주기는 다시 걸지 않는다', async () => {
        const tab = visibility(false);
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: tab.adapter });
        poller.start();
        poller.poke();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).not.toHaveBeenCalled();
        poller.poke({ evenIfHidden: true });
        await vi.advanceTimersByTimeAsync(10000);
        expect(run).toHaveBeenCalledTimes(1);
        poller.stop();
    });

    it('요청 중에 받은 evenIfHidden은 끝난 뒤의 재조회에도 유지한다', async () => {
        const tab = visibility(false);
        const finishes: Array<() => void> = [];
        const run = vi.fn(() => new Promise<void>(resolve => { finishes.push(resolve); }));
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: tab.adapter });
        poller.start();
        poller.poke({ evenIfHidden: true });
        await vi.advanceTimersByTimeAsync(0);
        poller.poke();
        poller.poke({ evenIfHidden: true });
        finishes[0]();
        await vi.advanceTimersByTimeAsync(0);
        expect(run).toHaveBeenCalledTimes(2);
        poller.stop();
    });

    it('멈춘 뒤에는 evenIfHidden도 조회하지 않는다', async () => {
        const run = vi.fn(async () => {});
        const poller = createPoller({ interval: 1000, maxInterval: 8000, run, visibility: visibility(false).adapter });
        poller.start();
        poller.stop();
        poller.poke({ evenIfHidden: true });
        poller.reschedule();
        await vi.advanceTimersByTimeAsync(10000);
        expect(run).not.toHaveBeenCalled();
    });
});
