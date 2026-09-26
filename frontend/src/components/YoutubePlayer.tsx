import { youtubeEmbedUrl } from '../utils/youtube.ts'

interface YoutubePlayerProps {
  /** 서버가 저장한 11자 영상 ID (또는 utils/youtube.ts 파서를 통과한 값) */
  videoId: string
  title: string
}

/** 16:9 유튜브 플레이어 (youtube-nocookie embed). 게임 상세와 폼 미리보기가 공유한다. */
export default function YoutubePlayer({ videoId, title }: YoutubePlayerProps) {
  return (
    <div className="aspect-video w-full overflow-hidden rounded border border-gray-200 bg-black">
      <iframe
        src={youtubeEmbedUrl(videoId)}
        title={title}
        loading="lazy"
        allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
        referrerPolicy="strict-origin-when-cross-origin"
        allowFullScreen
        className="h-full w-full"
      />
    </div>
  )
}
