interface PagePlaceholderProps {
  title: string
  phase: string
}

/** 아직 구현되지 않은 화면의 자리표시자 (각 단계에서 실제 화면으로 교체) */
export default function PagePlaceholder({ title, phase }: PagePlaceholderProps) {
  return (
    <section className="py-8">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-gray-500">{phase}에서 구현 예정입니다.</p>
    </section>
  )
}
