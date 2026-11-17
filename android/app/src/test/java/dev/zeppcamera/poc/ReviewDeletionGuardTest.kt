package dev.zeppcamera.poc
import org.junit.Test
import org.junit.Assert.*
class ReviewDeletionGuardTest {
 @Test fun onlyExactReviewedPhotoCanBeClaimedOnce(){val g=ReviewDeletionGuard();val t=g.issue("photo-one");assertNull(g.consume("wrong"));assertEquals("photo-one",g.consume(t));assertNull(g.consume(t))}
 @Test fun laterReviewAndExpiryInvalidateOldTokens(){var now=0L;val g=ReviewDeletionGuard{now};val old=g.issue("old");val current=g.issue("current");assertNull(g.consume(old));now=300001;assertNull(g.consume(current));assertNull(g.consume(""))}
}
