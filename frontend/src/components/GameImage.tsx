interface GameImageProps {
  imageUrl: string | null
  /** 이미지 대체 텍스트 (게임 이름) */
  name: string
  /** 크기·모서리 등 배치 클래스. 4:3 비율은 이 컴포넌트가 잡는다 */
  className?: string
}

/** 게임 대표 이미지. 이미지가 없으면 기본 이미지 파일 대신 회색 박스에 🎲 를 그린다. */
export default function GameImage({ imageUrl, name, className = '' }: GameImageProps) {
  const base = `aspect-[4/3] overflow-hidden rounded border border-gray-200 ${className}`
  if (imageUrl === null) {
    return (
      <div role="img" aria-label={`${name} (이미지 없음)`} className={`flex items-center justify-center bg-gray-100 text-2xl ${base}`}>
        🎲
      </div>
    )
  }
  return <img src={imageUrl} alt={name} loading="lazy" className={`bg-gray-100 object-cover ${base}`} />
}
