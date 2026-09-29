interface PageTitleProps {
  children: string
  /** 제목 아래 한 줄 설명 */
  lede?: string
}

/** 페이지 제목(display 글꼴, lg 에서 32px). */
export default function PageTitle({ children, lede }: PageTitleProps) {
  return (
    <div>
      <h1 className="font-display text-display lg:text-display-lg">{children}</h1>
      {lede && <p className="text-small text-ink-muted lg:text-body">{lede}</p>}
    </div>
  )
}
