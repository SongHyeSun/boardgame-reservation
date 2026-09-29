interface EmptyMessageProps {
  message: string
}

export default function EmptyMessage({ message }: EmptyMessageProps) {
  return <p className="py-8 text-center text-ink-muted">{message}</p>
}
