package dev.zeppcamera.poc
import org.junit.Test
import org.junit.Assert.*
class LocalSessionGateTest{
 @Test fun approvalIsRequired(){var n=0;val g=LocalSessionGate(random={"secret-${n++}"});g.openWindow();val ticket=g.enroll();assertFalse(g.authorized(ticket));assertFalse(g.status(ticket).first);val token=g.approve(ticket,true)!!;assertTrue(g.status(ticket).first);assertTrue(g.authorized(token));assertFalse(g.authorized("wrong"));assertThrows(IllegalStateException::class.java){g.enroll()};g.forget();assertFalse(g.authorized(token))}
 @Test fun noTicketDisclosureAndExpiry(){var time=100000L;val g=LocalSessionGate(now={time},random={"private"});assertThrows(IllegalStateException::class.java){g.enroll()};g.openWindow();val ticket=g.enroll();assertThrows(IllegalStateException::class.java){g.enroll()};assertThrows(IllegalStateException::class.java){g.status("wrong")};time+=90001;assertThrows(IllegalStateException::class.java){g.approve(ticket,true)}}
 @Test fun rememberedCredentialDoesNotNeedNewUserKey(){val g=LocalSessionGate("persisted-long-capability");assertTrue(g.hasToken());assertTrue(g.authorized("persisted-long-capability"));g.closeWindow();assertTrue(g.authorized("persisted-long-capability"))}
}
