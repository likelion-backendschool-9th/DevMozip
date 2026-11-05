'use client';
import { AuthShell } from '@/app/_components/AuthShell';
import { KakaoNotice } from '@/app/_components/KakaoNotice';
import { KakaoStartButton } from '@/app/_components/KakaoStartButton';
import styles from './page.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { emptySignup, careerOptions } from '@/mocks/auth';
import { signupOptionsAction } from '@/lib/auth/actions';
import { KAKAO_DUPLICATE_EMAIL_MESSAGE } from '@/lib/auth/kakao';
import { validEmail, validNickname, validateKakaoSignup, validateSignup, normalizeEmail } from '@/lib/account/authService';
import type { SignupErrors, SignupInput, SignupOptions } from '@/types/auth';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
function SignupForm() {
    const { signup, checkAvailability } = useAuth();
    const { notify } = usePrototype();
    const navigate = useNavigate();
    // 카카오 인증을 마치고 돌아온 가입. 비밀번호를 받지 않는다.
    const kakaoMode = useSearchParams().get('kakao') === 'signup';
    const [form, setForm] = useState<SignupInput>(() => structuredClone(emptySignup));
    const [errors, setErrors] = useState<SignupErrors>({});
    const [checkedEmail, setCheckedEmail] = useState('');
    const [checkedNickname, setCheckedNickname] = useState('');
    const [busy, setBusy] = useState(false);
    const [checking, setChecking] = useState<'email' | 'nickname' | null>(null);
    const [failure, setFailure] = useState('');
    const [options, setOptions] = useState<SignupOptions | null>(null);
    const [optionsError, setOptionsError] = useState('');
    const [optionsAttempt, setOptionsAttempt] = useState(0);
    useEffect(() => {
        let active = true;
        signupOptionsAction()
            .then(result => {
                if (!active) return;
                if ('error' in result) setOptionsError(result.error);
                else setOptions(result);
            })
            .catch(() => {
                if (active) setOptionsError('선택 항목을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
            });
        return () => {
            active = false;
        };
    }, [optionsAttempt]);
    const edit =<K extends keyof SignupInput>(key: K, value: SignupInput[K]) => {
        setForm(prev => ({ ...prev, [key]: value }));
        setErrors(prev => ({ ...prev, [key]: undefined, checks: undefined }));
        setFailure('');
        if (key === 'email') setCheckedEmail('');
        if (key === 'nickname') setCheckedNickname('');
    };
    const duplicate = async (field: 'email' | 'nickname') => {
        const value = form[field];
        if (!(field === 'email' ? validEmail(value) : validNickname(value))) {
            setErrors(prev => ({
                ...prev,
                [field]:
                    field === 'email'
                        ? '올바른 이메일 주소를 입력해 주세요.'
                        : '닉네임은 한글·영문·숫자·공백·밑줄 2~20자입니다.',
            }));
            return;
        }
        let available = false;
        let message = kakaoMode && field === 'email' ? KAKAO_DUPLICATE_EMAIL_MESSAGE : '이미 사용 중입니다.';
        setChecking(field);
        try {
            const result = await checkAvailability(field, value);
            if ('error' in result) message = result.error;
            else available = result.available;
        } catch {
            message = '중복 확인을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
        } finally {
            setChecking(null);
        }
        setErrors(prev => ({ ...prev, [field]: available ? undefined : message }));
        if (available) {
            if (field === 'email') setCheckedEmail(normalizeEmail(value));
            else setCheckedNickname(value.trim());
        } else {
            if (field === 'email') setCheckedEmail('');
            else setCheckedNickname('');
        }
    };
    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        const next = kakaoMode ? validateKakaoSignup(form) : validateSignup(form);
        if (checkedEmail !== normalizeEmail(form.email) || checkedNickname !== form.nickname.trim())
            next.checks = '이메일과 닉네임 중복 확인을 완료해 주세요.';
        setErrors(next);
        if (Object.keys(next).length) return;
        setBusy(true);
        try {
            await signup(form, kakaoMode ? { kakao: true } : undefined);
            setForm(structuredClone(emptySignup));
            notify('회원가입이 완료되었습니다. 프로필 온보딩을 시작해 주세요.');
            navigate('/onboarding');
        } catch (err) {
            setFailure(err instanceof Error ? err.message : '회원가입하지 못했습니다.');
        } finally {
            setBusy(false);
        }
    };
    const err = (key: keyof SignupErrors) =>
        errors[key] && (
            <p className={[styles.scope, 'form-error'].join(' ')} id={`signup-${key}-error`} role="alert">
                {errors[key]}
            </p>
        );
    return (
        <form className={[styles.scope, 'auth-form signup-form'].join(' ')} onSubmit={submit} noValidate>
            <KakaoNotice />
            <label htmlFor="signup-email">이메일</label>
            <div className={[styles.scope, 'check-field'].join(' ')}>
                <Input
                    id="signup-email"
                    type="email"
                    autoComplete="email"
                    value={form.email}
                    onChange={e => edit('email', e.target.value)}
                    maxLength={254}
                    placeholder="you@example.com"
                    disabled={busy || checking === 'email'}
                    aria-invalid={!!errors.email}
                    aria-describedby={errors.email ? 'signup-email-error' : undefined}
                />
                <Button type="button" variant="secondary" disabled={busy || checking !== null} onClick={() => duplicate('email')}>
                    중복 확인
                </Button>
            </div>
            {err('email')}
            {checkedEmail && (
                <p className={[styles.scope, 'check-success'].join(' ')} role="status">
                    사용 가능한 이메일입니다.
                </p>
            )}
            {!kakaoMode && (
                <div className={[styles.scope, 'auth-two-columns'].join(' ')}>
                    <div>
                        <label htmlFor="signup-password">비밀번호</label>
                        <Input
                            id="signup-password"
                            type="password"
                            autoComplete="new-password"
                            value={form.password}
                            maxLength={64}
                            onChange={e => edit('password', e.target.value)}
                            placeholder="영문·숫자 포함 8~64자"
                            disabled={busy}
                            aria-invalid={!!errors.password}
                        />
                        {err('password')}
                    </div>
                    <div>
                        <label htmlFor="signup-confirm">비밀번호 확인</label>
                        <Input
                            id="signup-confirm"
                            type="password"
                            autoComplete="new-password"
                            value={form.confirmPassword}
                            maxLength={64}
                            onChange={e => edit('confirmPassword', e.target.value)}
                            placeholder="한 번 더 입력해 주세요"
                            disabled={busy}
                            aria-invalid={!!errors.confirmPassword}
                        />
                        {err('confirmPassword')}
                    </div>
                </div>
            )}
            <label htmlFor="signup-nickname">닉네임</label>
            <div className={[styles.scope, 'check-field'].join(' ')}>
                <Input
                    id="signup-nickname"
                    value={form.nickname}
                    maxLength={20}
                    onChange={e => edit('nickname', e.target.value)}
                    placeholder="함께 부를 이름, 2~20자"
                    autoComplete="nickname"
                    disabled={busy || checking === 'nickname'}
                    aria-invalid={!!errors.nickname}
                />
                <Button type="button" variant="secondary" onClick={() => duplicate('nickname')} disabled={busy || checking !== null}>
                    중복 확인
                </Button>
            </div>
            {err('nickname')}
            {checkedNickname && (
                <p className={[styles.scope, 'check-success'].join(' ')} role="status">
                    사용 가능한 닉네임입니다.
                </p>
            )}
            {optionsError && (
                <div role="alert">
                    <p className={[styles.scope, 'form-error'].join(' ')}>{optionsError}</p>
                    <Button
                        type="button"
                        variant="secondary"
                        onClick={() => {
                            setOptionsError('');
                            setOptionsAttempt(attempt => attempt + 1);
                        }}
                    >
                        다시 불러오기
                    </Button>
                </div>
            )}
            <div className={[styles.scope, 'auth-two-columns'].join(' ')}>
                <div>
                    <label htmlFor="signup-job">개발 직군</label>
                    <select
                        id="signup-job"
                        className={[styles.scope, 'form-input'].join(' ')}
                        value={form.job}
                        onChange={e => edit('job', e.target.value)}
                        disabled={busy}
                        aria-invalid={!!errors.job}
                    >
                        <option value="">직군 선택</option>
                        {options?.jobCategories.map(j => (
                            <option key={j}>{j}</option>
                        ))}
                    </select>
                    {err('job')}
                </div>
                <div>
                    <label htmlFor="signup-career">경력</label>
                    <select
                        id="signup-career"
                        className={[styles.scope, 'form-input'].join(' ')}
                        value={form.career}
                        onChange={e => edit('career', e.target.value)}
                        disabled={busy}
                        aria-invalid={!!errors.career}
                    >
                        <option value="">경력 선택</option>
                        {careerOptions.map(c => (
                            <option key={c}>{c}</option>
                        ))}
                    </select>
                    {err('career')}
                </div>
            </div>
            <fieldset disabled={busy}>
                <legend>
                    기술 스택 <span>하나 이상 선택</span>
                </legend>
                <div className={[styles.scope, 'chips'].join(' ')}>
                    {options?.skills.map(s => (
                        <button
                            type="button"
                            key={s}
                            aria-pressed={form.stacks.includes(s)}
                            className={[styles.scope, `chip ${form.stacks.includes(s) ? 'selected' : ''}`].join(' ')}
                            onClick={() =>
                                edit(
                                    'stacks',
                                    form.stacks.includes(s) ? form.stacks.filter(x => x !== s) : [...form.stacks, s],
                                )
                            }
                        >
                            {s}
                        </button>
                    ))}
                </div>
                {err('stacks')}
            </fieldset>
            <fieldset disabled={busy}>
                <legend>
                    관심 분야 <span>하나 이상 선택</span>
                </legend>
                <div className={[styles.scope, 'chips'].join(' ')}>
                    {options?.interests.map(s => (
                        <button
                            type="button"
                            key={s}
                            aria-pressed={form.interests.includes(s)}
                            className={[styles.scope, `chip ${form.interests.includes(s) ? 'selected' : ''}`].join(' ')}
                            onClick={() =>
                                edit(
                                    'interests',
                                    form.interests.includes(s)
                                        ? form.interests.filter(x => x !== s)
                                        : [...form.interests, s],
                                )
                            }
                        >
                            {s}
                        </button>
                    ))}
                </div>
                {err('interests')}
            </fieldset>
            <label className={[styles.scope, 'checkbox-label availability'].join(' ')}>
                <input
                    type="checkbox"
                    checked={form.available}
                    onChange={e => edit('available', e.target.checked)}
                    disabled={busy}
                />
                현재 새로운 프로젝트에 협업할 수 있어요.
            </label>
            {err('checks')}
            {failure && (
                <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                    {failure}
                </p>
            )}
            <Button type="submit" className={[styles.scope, 'auth-submit'].join(' ')} disabled={busy || checking !== null || !options}>
                {busy ? '가입 정보를 저장하고 있어요…' : '회원가입'}
            </Button>
            {kakaoMode ? (
                <p className={[styles.scope, 'auth-switch'].join(' ')}>
                    카카오 인증이 만료되었나요? <Link href="/signup">처음부터 가입하기</Link>
                </p>
            ) : (
                <KakaoStartButton intent="signup">카카오로 가입하기</KakaoStartButton>
            )}
            <p className={[styles.scope, 'auth-switch'].join(' ')}>
                이미 계정이 있으신가요? <Link href="/login">로그인</Link>
            </p>
        </form>
    );
}

export default function SignupPage() {
    return (
        <AuthShell
            wide
            eyebrow="JOIN DEVMOZIP"
            title="새로운 연결을 시작하세요"
            description="당신의 기술과 관심사를 동료들에게 알려주세요."
        >
            <SignupForm />
        </AuthShell>
    );
}
