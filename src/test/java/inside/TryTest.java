package inside;

import inside.util.Try;
import org.junit.jupiter.api.Test;

import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;

public class TryTest{

    @Test
    public void jvmErrors(){
        assertThrows(VirtualMachineError.class, () -> Try.run(() -> {throw new InternalError();}));
        assertThrows(ThreadDeath.class, () -> Try.run(() -> {throw new ThreadDeath();}));
        assertThrows(LinkageError.class, () -> Try.run(() -> {throw new LinkageError();}));
        assertDoesNotThrow(() -> Try.run(() -> {throw new RuntimeException();}));
    }

    @Test
    public void andThen(){
        assertTrue(Try.success("ok").andThen(() -> {throw new RuntimeException();}).isFailure());
        assertTrue(Try.failure(new RuntimeException())
                .andThen(() -> {throw new RuntimeException();})
                .isFailure());
    }

    @Test
    public void filters(){
        Try<String> try0 = Try.success("ok");
        assertTrue(try0.filter("ok"::equals).isSuccess());
        assertTrue(try0.filter("no"::equals).isFailure());
        assertTrue(try0.filter("no"::equals, (Supplier<Throwable>)RuntimeException::new)
                .getCause() instanceof RuntimeException);
        assertTrue(try0.filter("no"::equals, (Function<String, Throwable>)RuntimeException::new)
                .getCause() instanceof RuntimeException);
    }

    @Test
    public void flatMap(){
        Try<String> try0 = Try.success("ok");
        assertNotEquals(try0, try0.flatMap(s -> Try.ofSupplier(() -> s + "!")));

        Try<String> try1 = Try.failure(new RuntimeException());
        assertEquals(try1, try1.flatMap(s -> Try.ofSupplier(() -> s + "!")));
    }

    @Test
    public void mappings(){
        Try<String> try0 = Try.success("ok")
                .map(s -> s + "!");
        assertEquals(Try.success("ok!"), try0);

        Try<String> try1 = Try.success("ok")
                .map(s -> {throw new RuntimeException();});
        assertTrue(try1.isFailure());
    }

    @Test
    public void andFinallyActions(){
        Try<String> try0 = Try.success("ok");
        assertTrue(try0.andFinally(() -> {throw new RuntimeException();}).isFailure());
        assertTrue(try0.andFinally(() -> {}).isSuccess());
    }

    @Test
    public void recovers(){
        Try<String> try0 = Try.failure(new RuntimeException());
        assertTrue(try0.recover(t -> "ok").isSuccess());
        assertTrue(try0.recoverWith(t -> Try.success("ok")).isSuccess());
        assertTrue(try0.recoverWith(t -> Try.failure(new AssertionError())).isFailure());
        assertTrue(try0.recover(Throwable.class, t -> "no").isSuccess());
        assertTrue(try0.recoverWith(Throwable.class, t -> Try.success("no")).isSuccess());
        assertTrue(try0.recoverWith(Throwable.class, t -> Try.failure(new AssertionError())).isFailure());
    }

    @Test
    public void transforms(){
        Try<String> try0 = Try.success("ok");
        assertEquals("ok", try0.transform(Try::get));
        assertThrows(UnsupportedOperationException.class, () -> try0.transform(Try::getCause));
    }

    @Test
    public void hashCodes(){
        Try<String> try0 = Try.success("ok");
        Try<String> try1 = Try.success("ok");
        assertEquals(try0.hashCode(), try1.hashCode());

        Try<String> try2 = Try.failure(new Throwable());
        Try<String> try3 = Try.failure(new Throwable());
        assertNotEquals(try2.hashCode(), try3.hashCode()); // Throwable doesn't have a #hashCode implementation
    }

    @Test
    public void equalsAndIdentity(){
        Try<String> try0 = Try.success("ok");
        Try<String> try1 = Try.success("ok");
        assertEquals(try0, try1);
        assertSame(try0, try0);
        assertNotSame(try0, try1);

        Try<String> try2 = Try.failure(new Throwable());
        Try<String> try3 = Try.failure(new Throwable());
        assertNotEquals(try2, try3);
        assertSame(try2, try2);
        assertNotSame(try2, try3);
    }
}
