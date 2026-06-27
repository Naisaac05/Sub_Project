import assert from 'node:assert/strict';
import test from 'node:test';

import { finishSuccessfulStream } from './ai-review-stream.ts';

test('successful stream completion releases the reader without cancelling the response', () => {
  let released = false;
  let cancelled = false;
  let aborted = false;
  const reader = {
    releaseLock() {
      released = true;
    },
    cancel() {
      cancelled = true;
      return Promise.resolve();
    },
  };
  const controller = {
    abort() {
      aborted = true;
    },
  };

  finishSuccessfulStream(reader, controller);

  assert.equal(released, true);
  assert.equal(cancelled, false);
  assert.equal(aborted, false);
});
