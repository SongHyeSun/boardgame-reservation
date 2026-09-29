export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'icon'
export type ButtonSize = 'sm' | 'md' | 'lg'

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary:
    'mb-[3px] border-transparent bg-felt text-on-felt shadow-token hover:bg-felt-strong active:translate-y-0.5 active:shadow-[0_1px_0_var(--color-felt-edge)] motion-reduce:active:translate-y-0',
  secondary: 'border-line-strong bg-surface text-ink hover:bg-sunken',
  ghost: 'border-transparent bg-transparent text-felt hover:bg-felt-soft',
  danger: 'border-danger bg-surface text-danger hover:bg-danger-soft',
  // 아이콘 전용(닫기 등). size 를 무시하고 40px 원형
  icon: 'size-10 rounded-full border-transparent bg-transparent p-0 text-ink-muted hover:bg-sunken hover:text-ink',
}

const SIZE_CLASS: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-[14px]',
  md: 'h-11 px-[18px] text-body',
  lg: 'h-[52px] px-6 text-[16px]',
}

export interface ButtonStyle {
  variant?: ButtonVariant
  size?: ButtonSize
  /** 가로 꽉 채움 */
  block?: boolean
}

/** 버튼 모양의 className. <Link> 를 버튼처럼 보이게 할 때도 쓴다 */
export function buttonClass({ variant = 'primary', size = 'md', block = false }: ButtonStyle = {}): string {
  const sizeClass = variant === 'icon' ? '' : `${SIZE_CLASS[size]} rounded-md`
  const blockClass = block ? 'flex w-full' : 'inline-flex'
  return `${blockClass} items-center justify-center gap-1.5 whitespace-nowrap border font-semibold transition-[transform,box-shadow,background-color] duration-100 motion-reduce:transition-none disabled:cursor-not-allowed disabled:translate-y-0 disabled:border-transparent disabled:bg-sunken disabled:text-ink-muted disabled:shadow-none ${sizeClass} ${VARIANT_CLASS[variant]}`
}
