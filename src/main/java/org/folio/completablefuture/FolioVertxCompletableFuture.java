package org.folio.completablefuture;


import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * An implementation of {@link CompletableFuture} for Vert.x.4.x.x  It differs in the way to handle async calls:
 * <p>
 * * {@link FolioVertxCompletableFuture} are attached to a Vert.x {@link Context}
 * * All operator methods returns {@link FolioVertxCompletableFuture}
 * * <em*async</em> method not passing an {@link Executor} are executed on the attached {@link Context}
 * * All non async method are executed on the current Thread (so not necessary on the attached {@link Context}
 * <p>
 * The class also offer bridges methods with Vert.x {@link Future}, and regular {@link CompletableFuture}.
 *
 * @param <T> the expected type of result
 */
@SuppressWarnings("WeakerAccess")
public class FolioVertxCompletableFuture<T> extends CompletableFuture<T> implements CompletionStage<T> {
    private final Executor executor;

    /**
     * The {@link Context} used by the future.
     */
    private final Context context;

    // ============= Constructors =============

    /**
     * Creates an instance of {@link FolioVertxCompletableFuture}, using the current Vert.x context or create a new one.
     *
     * @param vertx the Vert.x instance
     */
    public FolioVertxCompletableFuture(Vertx vertx) {
        this(Objects.requireNonNull(vertx).getOrCreateContext());
    }

    /**
     * Creates an instance of {@link FolioVertxCompletableFuture}, using the given {@link Context}.
     *
     * @param context the context
     */
    public FolioVertxCompletableFuture(Context context) {
        this.context = Objects.requireNonNull(context);
        this.executor = command -> context.runOnContext(v -> command.run());
    }

    /**
     * Creates a new {@link FolioVertxCompletableFuture} from the given context and given {@link CompletableFuture}.
     * The created {@link FolioVertxCompletableFuture} is completed successfully or not when the given completable future
     * completes successfully or not.
     *
     * @param context the context
     * @param future  the completable future
     */
    private FolioVertxCompletableFuture(Context context, CompletableFuture<T> future) {
        this(context);
        Objects.requireNonNull(future).whenComplete((res, err) -> {
            if (err != null) {
                completeExceptionally(err);
            } else {
                complete(res);
            }
        });
    }

    /**
     * Creates a new {@link FolioVertxCompletableFuture} using the current {@link Context}. This method
     * <strong>must</strong> be used from a Vert.x thread, or fails.
     */
    public FolioVertxCompletableFuture() {
        this(Vertx.currentContext());
    }

    // ============= Factory methods (from) =============

    /**
     * Creates a new {@link FolioVertxCompletableFuture} from the given {@link Vertx} instance and given
     * {@link CompletableFuture}. The returned future uses the current Vert.x context, or creates a new one.
     * <p>
     * The created {@link FolioVertxCompletableFuture} is completed successfully or not when the given completable future
     * completes successfully or not.
     *
     * @param vertx  the Vert.x instance
     * @param future the future
     * @param <T>    the type of the result
     * @return the new {@link FolioVertxCompletableFuture}
     */
    public static <T> FolioVertxCompletableFuture<T> from(Vertx vertx, CompletableFuture<T> future) {
        return from(vertx.getOrCreateContext(), future);
    }

    /**
     * Creates a new {@link FolioVertxCompletableFuture} from the given {@link Context} instance and given
     * {@link Future}. The returned future uses the current Vert.x context, or creates a new one.
     * <p>
     * The created {@link FolioVertxCompletableFuture} is completed successfully or not when the given future
     * completes successfully or not.
     *
     * @param vertx  the Vert.x instance
     * @param future the Vert.x future
     * @param <T>    the type of the result
     * @return the new {@link FolioVertxCompletableFuture}
     */
    public static <T> FolioVertxCompletableFuture<T> from(Vertx vertx, Future<T> future) {
        return from(vertx.getOrCreateContext(), future);
    }

    /**
     * Creates a {@link FolioVertxCompletableFuture} from the given {@link Context} and {@link CompletableFuture}.
     * <p>
     * The created {@link FolioVertxCompletableFuture} is completed successfully or not when the given future
     * completes successfully or not. The completion is called on the given {@link Context}, immediately if it is
     * already executing on the right context, asynchronously if not.
     *
     * @param context the context
     * @param future  the future
     * @param <T>     the type of result
     * @return the creation {@link FolioVertxCompletableFuture}
     */
    public static <T> FolioVertxCompletableFuture<T> from(Context context, CompletableFuture<T> future) {
        FolioVertxCompletableFuture<T> res = new FolioVertxCompletableFuture<>(Objects.requireNonNull(context));
        Objects.requireNonNull(future).whenComplete((result, error) -> {
            if (context == Vertx.currentContext()) {
                res.complete(result, error);
            } else {
                res.context.runOnContext(v -> res.complete(result, error));
            }
        });
        return res;
    }

    /**
     * Creates a new {@link FolioVertxCompletableFuture} from the given {@link Context} instance and given
     * {@link Future}. The returned future uses the current Vert.x context, or creates a new one.
     * <p>
     * The created {@link FolioVertxCompletableFuture} is completed successfully or not when the given future
     * completes successfully or not. The created {@link FolioVertxCompletableFuture} is completed successfully or not
     * when the given future completes successfully or not. The completion is called on the given {@link Context},
     * immediately if it is already executing on the right context, asynchronously if not.
     *
     * @param context the context
     * @param future  the Vert.x future
     * @param <T>     the type of the result
     * @return the new {@link FolioVertxCompletableFuture}
     */
    public static <T> FolioVertxCompletableFuture<T> from(Context context, Future<T> future) {
        FolioVertxCompletableFuture<T> res = new FolioVertxCompletableFuture<>(Objects.requireNonNull(context));
        Objects.requireNonNull(future).onComplete(ar -> {
            if (context == Vertx.currentContext()) {
                res.completeFromAsyncResult(ar);
            } else {
                res.context.runOnContext(v -> res.completeFromAsyncResult(ar));
            }
        });
        return res;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the current Vert.x
     * {@link Context} with the value obtained by calling the given Supplier.
     * <p>
     * This method is different from {@link CompletableFuture#supplyAsync(Supplier)} as it does not use a fork join
     * executor, but use the Vert.x context.
     *
     * @param context  the context in which the supplier is executed.
     * @param supplier a function returning the value to be used to complete the returned CompletableFuture
     * @param <T>      the function's return type
     * @return the new CompletableFuture
     */
    public static <T> FolioVertxCompletableFuture<T> supplyAsync(Context context, Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        FolioVertxCompletableFuture<T> future = new FolioVertxCompletableFuture<>(Objects.requireNonNull(context));
        context.runOnContext(v -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the
     * current Vert.x {@link Context} after it runs the given action.
     * <p>
     * This method is different from {@link CompletableFuture#runAsync(Runnable)} as it does not use a fork join
     * executor, but use the Vert.x context.
     *
     * @param context  the context
     * @param runnable the action to run before completing the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public static FolioVertxCompletableFuture<Void> runAsync(Context context, Runnable runnable) {
        Objects.requireNonNull(runnable);
        FolioVertxCompletableFuture<Void> future = new FolioVertxCompletableFuture<>(context);
        context.runOnContext(v -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the worker thread pool of
     * Vert.x
     * <p>
     * This method is different from {@link CompletableFuture#supplyAsync(Supplier)} as it does not use a fork join
     * executor, but the worker thread pool.
     *
     * @param vertx    the Vert.x instance
     * @param supplier a function returning the value to be used to complete the returned CompletableFuture
     * @param <T>      the function's return type
     * @return the new CompletableFuture
     */
    public static <T> FolioVertxCompletableFuture<T> supplyBlockingAsync(Vertx vertx, Supplier<T> supplier) {
        return supplyBlockingAsync(Objects.requireNonNull(vertx).getOrCreateContext(), supplier);
    }

    /**
     * Returns a new CompletableFuture that is asynchronously completed by a task running in the worker thread pool of
     * Vert.x
     * <p>
     * This method is different from {@link CompletableFuture#supplyAsync(Supplier)} as it does not use a fork join
     * executor, but the worker thread pool.
     *
     * @param context  the context in which the supplier is executed.
     * @param supplier a function returning the value to be used to complete the returned CompletableFuture
     * @param <T>      the function's return type
     * @return the new CompletableFuture
     */
    public static <T> FolioVertxCompletableFuture<T> supplyBlockingAsync(Context context, Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        FolioVertxCompletableFuture<T> future = new FolioVertxCompletableFuture<>(context);
        context.<T>executeBlocking(
                fut -> {
                    try {
                        fut.complete(supplier.get());
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                },
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                    } else {
                        future.complete(ar.result());
                    }
                }
        );
        return future;
    }

    // ============= Parallel composition methods =============

    /**
     * Returns a new CompletableFuture that is completed when all of the given CompletableFutures complete.  If any of
     * the given CompletableFutures complete exceptionally, then the returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.  Otherwise, the results, if any, of the given
     * CompletableFutures are not reflected in the returned CompletableFuture, but may be obtained by inspecting them
     * individually. If no CompletableFutures are provided, returns a CompletableFuture completed with the value
     * {@code null}.
     * <p>
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a
     * program, as in: {@code CompletableFuture.allOf(c1, c2, c3).join();}.
     * <p>
     * Unlike the original {@link CompletableFuture#allOf(CompletableFuture[])} this method invokes the dependent
     * stages into the Vert.x context.
     *
     * @param vertx   the Vert.x instance to retrieve the context
     * @param futures the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are {@code null}
     */
    public static FolioVertxCompletableFuture<Void> allOf(Vertx vertx, CompletableFuture<?>... futures) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        return FolioVertxCompletableFuture.from(vertx, all);
    }

    /**
     * Returns a new CompletableFuture that is completed when all of the given CompletableFutures complete.  If any of
     * the given CompletableFutures complete exceptionally, then the returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.  Otherwise, the results, if any, of the given
     * CompletableFutures are not reflected in the returned CompletableFuture, but may be obtained by inspecting them
     * individually. If no CompletableFutures are provided, returns a CompletableFuture completed with the value
     * {@code null}.
     * <p>
     * <p>Among the applications of this method is to await completion
     * of a set of independent CompletableFutures before continuing a
     * program, as in: {@code CompletableFuture.allOf(c1, c2, c3).join();}.
     * <p>
     * Unlike the original {@link CompletableFuture#allOf(CompletableFuture[])} this method invokes the dependent
     * stages into the Vert.x context.
     *
     * @param context the context
     * @param futures the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are {@code null}
     */
    public static FolioVertxCompletableFuture<Void> allOf(Context context, CompletableFuture<?>... futures) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        return FolioVertxCompletableFuture.from(context, all);
    }


    /**
     * @return the context associated with the current {@link FolioVertxCompletableFuture}.
     */
    public Context context() {
        return context;
    }

    // ============= Composite Future implementation =============

    @Override
    public <U> FolioVertxCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenApply(fn));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenApplyAsync(fn, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenAcceptAsync(action, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenRun(Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.thenRun(action));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> FolioVertxCompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenCombine(other, fn));
    }

    @Override
    public <U> FolioVertxCompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return new FolioVertxCompletableFuture<>(context, super.thenAcceptBoth(other, action));
    }

    @Override
    public <U> FolioVertxCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterBoth(other, action));
    }

    @Override
    public FolioVertxCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.applyToEither(other, fn));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return new FolioVertxCompletableFuture<>(context, super.acceptEither(other, action));
    }

    @Override
    public FolioVertxCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.acceptEitherAsync(other, action, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterEither(other, action));
    }

    @Override
    public FolioVertxCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenCompose(fn));
    }

    @Override
    public FolioVertxCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return new FolioVertxCompletableFuture<>(context, super.whenComplete(action));
    }

    @Override
    public FolioVertxCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.handle(fn));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.handleAsync(fn, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenApplyAsync(fn, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return new FolioVertxCompletableFuture<>(context, super.thenAccept(action));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return new FolioVertxCompletableFuture<>(context, super.thenAcceptAsync(action, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> thenRunAsync(Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> FolioVertxCompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other,
                                                                                                  BiFunction<? super T, ? super U, ? extends V> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                                                                                                     BiConsumer<? super T, ? super U> action) {
        return new FolioVertxCompletableFuture<>(context, super.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterBothAsync(other, action, executor));
    }


    @Override
    public <U> FolioVertxCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public FolioVertxCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return new FolioVertxCompletableFuture<>(context, super.acceptEitherAsync(other, action, executor));
    }


    @Override
    public FolioVertxCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return new FolioVertxCompletableFuture<>(context, super.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return new FolioVertxCompletableFuture<>(context, super.thenComposeAsync(fn, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenComposeAsync(fn, executor));
    }

    public <U, V> FolioVertxCompletableFuture<V> thenCombineAsync(
            CompletionStage<? extends U> other,
            BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return new FolioVertxCompletableFuture<>(context, super.thenCombineAsync(other, fn, executor));
    }

    @Override
    public FolioVertxCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return new FolioVertxCompletableFuture<>(context, super.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> FolioVertxCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return new FolioVertxCompletableFuture<>(context, super.handleAsync(fn, executor));
    }


    @Override
    public FolioVertxCompletableFuture<T> toCompletableFuture() {
        return this;
    }

    // ============= other instance methods =============


    private void complete(T result, Throwable error) {
        if (error == null) {
            super.complete(result);
        } else {
            super.completeExceptionally(error);
        }
    }

    private void completeFromAsyncResult(AsyncResult<T> ar) {
        if (ar.succeeded()) {
            super.complete(ar.result());
        } else {
            super.completeExceptionally(ar.cause());
        }
    }

}
