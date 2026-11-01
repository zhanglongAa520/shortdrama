// Existing demo records keep their IDs/URLs; use the bundled audio edition.
// Operator-supplied external media is passed through without modification.
export function playbackUrl(url) {
  const asset = /^asset:([1-9][0-9]*)$/.exec(url || '')
  if (asset) return `/api/admin/assets/${asset[1]}/content`
  const match = /^\/demo\/drama-([1-4])\.mp4$/.exec(url || '')
  const audioMatch = /^\/demo\/edit\/drama-([1-4])-sound\.mp4$/.exec(url || '')
  const id = match?.[1] || audioMatch?.[1]
  return id ? `/api/media/demo/drama-${id}.mp4` : url
}
