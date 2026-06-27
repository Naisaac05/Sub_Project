type ReleasableReader = Pick<ReadableStreamDefaultReader<Uint8Array>, 'releaseLock'>;
type AbortableController = Pick<AbortController, 'abort'>;

export function finishSuccessfulStream(
  reader: ReleasableReader | null,
  controller: AbortableController | null,
) {
  reader?.releaseLock();
  void controller;
}
