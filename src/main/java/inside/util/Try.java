package inside.util;

import inside.util.func.*;
import reactor.core.Exceptions;
import reactor.util.annotation.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;

public abstract class Try<T>{

    private Try(){

    }

    public static <T> Try<T> ofSupplier(Supplier<? extends T> supplier){
        Objects.requireNonNull(supplier, "supplier");
        return ofCallable(supplier::get);
    }

    public static <T> Try<T> ofCallable(Callable<? extends T> callable){
        Objects.requireNonNull(callable, "callable");
        try{
            return new Success<>(callable.call());
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public static Try<Void> runRunnable(Runnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        return run(runnable::run);
    }

    public static Try<Void> run(UnsafeRunnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        try{
            runnable.run();
            return new Success<>(null);
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public static <T> Try<T> success(@Nullable T value){
        return new Success<>(value);
    }

    public static <T> Try<T> failure(Throwable exception){
        return new Failure<>(exception);
    }

    @SuppressWarnings("unchecked")
    public static <T> Try<T> narrow(Try<? extends T> t){
        return (Try<T>)t;
    }

    public final Optional<T> toOptional(){
        return isSuccess() ? Optional.ofNullable(get()) : Optional.empty();
    }

    public final Try<T> andThen(Consumer<? super T> consumer){
        Objects.requireNonNull(consumer, "consumer");
        return andThenTry(consumer::accept);
    }

    public final Try<T> andThenTry(UnsafeConsumer<? super T> consumer){
        Objects.requireNonNull(consumer, "consumer");
        if(isFailure()){
            return this;
        }
        try{
            consumer.accept(get());
            return this;
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public final Try<T> andThen(Runnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        return andThenTry(runnable::run);
    }

    public final Try<T> andThenTry(UnsafeRunnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        if(isFailure()){
            return this;
        }
        try{
            runnable.run();
            return this;
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public final Try<Throwable> failed(){
        if(isFailure()){
            return new Success<>(getCause());
        }
        return new Failure<>(new NoSuchElementException("Success.failed()"));
    }

    public final Try<T> filter(Predicate<? super T> predicate, Supplier<? extends Throwable> supplier){
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(supplier, "supplier");
        return filterTry(predicate::test, supplier);
    }

    public final Try<T> filter(Predicate<? super T> predicate, Function<? super T, ? extends Throwable> supplier){
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(supplier, "supplier");
        return filterTry(predicate::test, supplier::apply);
    }

    public final Try<T> filter(Predicate<? super T> predicate){
        Objects.requireNonNull(predicate, "predicate");
        return filterTry(predicate::test);
    }

    public final Try<T> filterTry(UnsafePredicate<? super T> predicate){
        Objects.requireNonNull(predicate, "predicate");
        return filterTry(predicate, () -> new NoSuchElementException("Predicate does not hold for " + get()));
    }

    public final Try<T> filterTry(UnsafePredicate<? super T> predicate, Supplier<? extends Throwable> supplier){
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(supplier, "supplier");
        if(isFailure()){
            return this;
        }
        try{
            if(predicate.test(get())){
                return this;
            }
            return new Failure<>(supplier.get());
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public final Try<T> filterTry(UnsafePredicate<? super T> predicate, UnsafeFunction<? super T, ? extends Throwable> supplier){
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(supplier, "supplier");
        return flatMapTry(t -> predicate.test(t) ? this : failure(supplier.apply(t)));
    }

    public final <U> Try<U> flatMap(Function<? super T, ? extends Try<? extends U>> mapper){
        Objects.requireNonNull(mapper, "mapper is null");
        return flatMapTry(mapper::apply);
    }

    @SuppressWarnings("unchecked")
    public final <U> Try<U> flatMapTry(UnsafeFunction<? super T, ? extends Try<? extends U>> mapper){
        Objects.requireNonNull(mapper, "mapper");
        if(isFailure()){
            return (Failure<U>)this;
        }
        try{
            return (Try<U>)mapper.apply(get());
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    @Nullable
    public abstract T get();

    public abstract Throwable getCause();

    public abstract boolean isFailure();

    public abstract boolean isSuccess();

    public final <U> Try<U> map(Function<? super T, ? extends U> mapper){
        Objects.requireNonNull(mapper, "mapper");
        return mapTry(mapper::apply);
    }

    public final <U> Try<U> mapTry(UnsafeFunction<? super T, ? extends U> mapper){
        Objects.requireNonNull(mapper, "mapper");
        if(isFailure()){
            return (Failure<U>)this;
        }
        try{
            return new Success<>(mapper.apply(get()));
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    public final Try<T> onFailure(Consumer<? super Throwable> consumer){
        Objects.requireNonNull(consumer, "consumer");
        if(isFailure()){
            consumer.accept(getCause());
        }
        return this;
    }

    public final Try<T> onFailure(Predicate<? super Throwable> predicate, Consumer<? super Throwable> consumer){
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(consumer, "consumer");
        if(isFailure() && predicate.test(getCause())){
            consumer.accept(getCause());
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public final <X extends Throwable> Try<T> onFailure(Class<X> exceptionType, Consumer<? super X> consumer){
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(consumer, "consumer");
        if(isFailure() && exceptionType.isAssignableFrom(getCause().getClass())){
            consumer.accept((X)getCause());
        }
        return this;
    }

    public final Try<T> onSuccess(Consumer<? super T> action){
        Objects.requireNonNull(action, "action");
        if(isSuccess()){
            action.accept(get());
        }
        return this;
    }


    @Nullable
    public final T orElse(@Nullable T other){
        return isSuccess() ? get() : other;
    }

    @SuppressWarnings("unchecked")
    public final Try<T> or(Try<? extends T> other){
        Objects.requireNonNull(other, "other");
        return isSuccess() ? this : (Try<T>)other;
    }

    @SuppressWarnings("unchecked")
    public final Try<T> orGet(Supplier<? extends Try<? extends T>> supplier){
        Objects.requireNonNull(supplier, "supplier");
        return isSuccess() ? this : (Try<T>)supplier.get();
    }

    @Nullable
    public final T orElseGet(Supplier<? extends T> supplier){
        Objects.requireNonNull(supplier, "supplier");
        return isFailure() ? supplier.get() : get();
    }

    @Nullable
    public final T orElseGet(Function<? super Throwable, ? extends T> other){
        Objects.requireNonNull(other, "other");
        return isFailure() ? other.apply(getCause()) : get();
    }

    public final void orElseRun(Consumer<? super Throwable> supplier){
        Objects.requireNonNull(supplier, "supplier");
        if(isFailure()){
            supplier.accept(getCause());
        }
    }

    @Nullable
    public final <X extends Throwable> T getOrElseThrow(Function<? super Throwable, X> mapper) throws X{
        Objects.requireNonNull(mapper, "mapper");
        if(isFailure()){
            throw mapper.apply(getCause());
        }
        return get();
    }

    public final <X> X fold(Function<? super Throwable, ? extends X> ifFail, Function<? super T, ? extends X> function){
        if(isFailure()){
            return ifFail.apply(getCause());
        }
        return function.apply(get());
    }

    public final Try<T> peek(Consumer<? super Throwable> failureAction, Consumer<? super T> successAction){
        Objects.requireNonNull(failureAction, "failureAction");
        Objects.requireNonNull(successAction, "successAction");
        if(isFailure()){
            failureAction.accept(getCause());
        }else{
            successAction.accept(get());
        }
        return this;
    }

    public final Try<T> peek(Consumer<? super T> consumer){
        Objects.requireNonNull(consumer, "consumer");
        if(isSuccess()){
            consumer.accept(get());
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public final <X extends Throwable> Try<T> recover(Class<X> exceptionType, Function<? super X, ? extends T> function){
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(function, "function");
        if(isFailure()){
            Throwable cause = getCause();
            if(exceptionType.isAssignableFrom(cause.getClass())){
                return Try.ofCallable(() -> function.apply((X)cause));
            }
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public final <X extends Throwable> Try<T> recoverWith(Class<X> exceptionType, Function<? super X, Try<? extends T>> tryFunction){
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(tryFunction, "tryFunction");
        if(isFailure()){
            Throwable cause = getCause();
            if(exceptionType.isAssignableFrom(cause.getClass())){
                try{
                    return narrow(tryFunction.apply((X)cause));
                }catch(Throwable t){
                    return new Failure<>(t);
                }
            }
        }
        return this;
    }

    public final <X extends Throwable> Try<T> recoverWith(Class<X> exceptionType, Try<? extends T> recovered){
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(recovered, "recovered");
        return isFailure() && exceptionType.isAssignableFrom(getCause().getClass())
               ? narrow(recovered)
               : this;
    }

    public final <X extends Throwable> Try<T> recover(Class<X> exceptionType, T value){
        Objects.requireNonNull(exceptionType, "exceptionType");
        return isFailure() && exceptionType.isAssignableFrom(getCause().getClass())
               ? Try.success(value)
               : this;
    }

    public final Try<T> recover(Function<? super Throwable, ? extends T> function){
        Objects.requireNonNull(function, "function");
        if(isFailure()){
            return Try.ofCallable(() -> function.apply(getCause()));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public final Try<T> recoverWith(Function<? super Throwable, ? extends Try<? extends T>> function){
        Objects.requireNonNull(function, "function");
        if(isFailure()){
            try{
                return (Try<T>)function.apply(getCause());
            }catch(Throwable t){
                return new Failure<>(t);
            }
        }
        return this;
    }

    public final <U> U transform(Function<? super Try<T>, ? extends U> function){
        Objects.requireNonNull(function, "function");
        return function.apply(this);
    }

    public final Try<T> andFinally(Runnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        return andFinallyTry(runnable::run);
    }

    public final Try<T> andFinallyTry(UnsafeRunnable runnable){
        Objects.requireNonNull(runnable, "runnable");
        try{
            runnable.run();
            return this;
        }catch(Throwable t){
            return new Failure<>(t);
        }
    }

    private static final class Success<T> extends Try<T>{
        @Nullable
        private final T value;

        private Success(@Nullable T value){
            this.value = value;
        }

        @Nullable
        @Override
        public T get(){
            return value;
        }

        @Override
        public Throwable getCause(){
            throw new UnsupportedOperationException("getCause on Success");
        }

        @Override
        public boolean isFailure(){
            return false;
        }

        @Override
        public boolean isSuccess(){
            return true;
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Success<?> success = (Success<?>)o;
            return Objects.equals(value, success.value);
        }

        @Override
        public int hashCode(){
            return Objects.hashCode(value);
        }

        @Override
        public String toString(){
            return "Success{" + value + '}';
        }
    }

    private static final class Failure<T> extends Try<T>{

        private final Throwable cause;

        private Failure(Throwable cause){
            Objects.requireNonNull(cause, "cause");
            Exceptions.throwIfJvmFatal(cause);
            this.cause = cause;
        }

        @Override
        public T get(){
            return sneakyThrow(cause);
        }

        @Override
        public Throwable getCause(){
            return cause;
        }

        @Override
        public boolean isFailure(){
            return true;
        }

        @Override
        public boolean isSuccess(){
            return false;
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            Failure<?> failure = (Failure<?>)o;
            return cause.equals(failure.cause);
        }

        @Override
        public int hashCode(){
            return Objects.hashCode(cause);
        }

        @Override
        public String toString(){
            return "Failure{" + cause + '}';
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T{
        throw (T)t;
    }
}
