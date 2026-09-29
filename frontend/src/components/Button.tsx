// 폼 제출 버튼은 type="submit"을 명시해야 함 (기본값은 button)
import type { ButtonHTMLAttributes } from 'react'
import { buttonClass, type ButtonStyle } from './buttonStyle.ts'

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'>, ButtonStyle {}

export default function Button({ variant, size, block, type = 'button', ...buttonProps }: ButtonProps) {
  return <button {...buttonProps} type={type} className={buttonClass({ variant, size, block })} />
}
