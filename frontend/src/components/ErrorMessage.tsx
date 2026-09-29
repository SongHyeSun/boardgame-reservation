interface ErrorMessageProps {
  message: string
}

export default function ErrorMessage({ message }: ErrorMessageProps) {
  return (
    <p role="alert" className="rounded-md border border-danger bg-danger-soft px-3 py-2 text-small font-medium text-danger">
      {message}
    </p>
  )
}
