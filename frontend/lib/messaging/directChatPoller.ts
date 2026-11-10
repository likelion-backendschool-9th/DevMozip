/** 화면이 보이는지 확인한다. 테스트에서 바꿀 수 있도록 document를 직접 쓰지 않는다. */
export interface PollerVisibility {
    isVisible: () => boolean;
    subscribe: (listener: () => void) => () => void;
}

export const documentVisibility: PollerVisibility = {
    isVisible: () => typeof document === 'undefined' || document.visibilityState !== 'hidden',
    subscribe: listener => {
        if (typeof document === 'undefined') return () => {};
        document.addEventListener('visibilitychange', listener);
        return () => document.removeEventListener('visibilitychange', listener);
    },
};

export interface PollerOptions {
    /** 함수면 타이머를 걸 때마다 다시 읽는다. WebSocket 연결 여부에 따라 주기를 바꿀 때 쓴다. */
    interval: number | (() => number);
    maxInterval: number;
    run: (signal: AbortSignal) => Promise<void>;
    onFailure?: (error: unknown) => void;
    visibility?: PollerVisibility;
}

export interface PokeOptions {
    /** 숨겨진 탭에서도 조회한다. 실시간 신호를 받았을 때 쓴다. */
    evenIfHidden?: boolean;
}

/**
 * 이전 요청이 끝난 뒤에 다음 타이머를 걸어 요청이 겹치지 않게 한다.
 * 탭이 숨겨지면 멈추고 돌아오면 바로 조회한다. 실패하면 간격을 두 배씩 늘리고, 성공하면 되돌린다.
 */
export function createPoller({ interval, maxInterval, run, onFailure, visibility = documentVisibility }: PollerOptions) {
    let active = false;
    let running = false;
    let rerun = false;
    let rerunEvenIfHidden = false;
    let failures = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let controller: AbortController | null = null;
    let unsubscribe: (() => void) | undefined;
    const baseInterval = () => (typeof interval === 'function' ? interval() : interval);

    const clearTimer = () => {
        clearTimeout(timer);
        timer = undefined;
    };
    const schedule = () => {
        clearTimer();
        if (!active || !visibility.isVisible()) return;
        timer = setTimeout(() => void tick(), Math.min(baseInterval() * 2 ** failures, maxInterval));
    };
    async function tick(evenIfHidden = false) {
        clearTimer();
        if (!active || (!evenIfHidden && !visibility.isVisible())) return;
        if (running) {
            rerun = true;
            rerunEvenIfHidden ||= evenIfHidden;
            return;
        }
        running = true;
        const current = new AbortController();
        controller = current;
        try {
            await run(current.signal);
            failures = 0;
        } catch (error) {
            if (!current.signal.aborted) {
                failures += 1;
                onFailure?.(error);
            }
        }
        running = false;
        if (rerun) {
            const hiddenToo = rerunEvenIfHidden;
            rerun = false;
            rerunEvenIfHidden = false;
            void tick(hiddenToo);
        } else schedule();
    }

    return {
        start() {
            if (active) return;
            active = true;
            failures = 0;
            unsubscribe = visibility.subscribe(() => {
                if (visibility.isVisible()) void tick();
                else clearTimer();
            });
            void tick();
        },
        stop() {
            active = false;
            rerun = false;
            rerunEvenIfHidden = false;
            clearTimer();
            controller?.abort();
            unsubscribe?.();
            unsubscribe = undefined;
        },
        /** 다음 주기를 기다리지 않고 조회한다. 요청 중이면 끝난 뒤에 한 번 더 조회한다. */
        poke(options: PokeOptions = {}) {
            void tick(options.evenIfHidden === true);
        },
        /** 바뀐 주기로 타이머를 다시 건다. 요청 중이면 끝난 뒤에 새 주기로 건다. */
        reschedule() {
            if (active && !running) schedule();
        },
    };
}
