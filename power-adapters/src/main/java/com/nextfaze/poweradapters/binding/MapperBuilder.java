package com.nextfaze.poweradapters.binding;

import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import com.nextfaze.poweradapters.PowerAdapter;
import com.nextfaze.poweradapters.Predicate;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nextfaze.poweradapters.binding.BinderWrapper.overrideLayout;

/**
 * Fluent-style builder that may be used to construct a type-safe, complex {@link Mapper}. This mapper evaluates a list
 * of rules for each class in an item's class hierarchy, and returns the {@link Binder} when a rule passes.
 */
public final class MapperBuilder {

    private static final Predicate<Object> ALWAYS = new Predicate<Object>() {
        @Override
        public boolean apply(Object o) {
            return true;
        }
    };

    @NonNull
    private final Map<Class<?>, List<Rule<?>>> mRules = new HashMap<>();

    @Nullable
    private Boolean mStableIds;

    /**
     * Map an item type to the specified binder. The specified layout resource, if {@code >0}, will be used to override
     * the {@link View} normally inflated by the specified binder.
     * <p>
     * In addition, this method accepts a {@link Predicate} that will be evaluated per-instance to determine if the
     * specified binder is suitable for use with the item.
     * @param itemClass The type of item accepted by the specified binder.
     * @param overrideItemLayoutResource The layout resource that will be inflated instead of the view provided by
     * {@link Binder#newView(ViewGroup)}. May be {@code 0}, in which case this parameter does nothing.
     * @param binder The binder to be used to bind the specified item type.
     * @param predicate A predicate that will be evaluated for each item instance to determine if the specified binder
     * is suitable.
     * @param <T> The type of item object.
     * @return This builder, to allow chaining.
     */
    @NonNull
    public <T> MapperBuilder bind(@NonNull Class<? extends T> itemClass,
                                  @LayoutRes int overrideItemLayoutResource,
                                  @NonNull Binder<? super T, ? extends View> binder,
                                  @NonNull Predicate<? super T> predicate) {
        List<Rule<?>> rules = mRules.get(itemClass);
        if (rules == null) {
            rules = new ArrayList<>();
        }
        mRules.put(itemClass, rules);
        rules.add(new Rule<>(predicate, overrideLayout(binder, overrideItemLayoutResource)));
        return this;
    }

    /**
     * Map an item type to the specified binder. The specified layout resource, if {@code >0}, will be used to override
     * the {@link View} normally inflated by the specified binder.
     * @param itemClass The type of item accepted by the specified binder.
     * @param overrideItemLayoutResource The layout resource that will be inflated instead of the view provided by
     * {@link Binder#newView(ViewGroup)}. May be {@code 0}, in which case this parameter does nothing.
     * @param binder The binder to be used to bind the specified item type.
     * @param <T> The type of item object.
     * @return This builder, to allow chaining.
     */
    @NonNull
    public <T> MapperBuilder bind(@NonNull Class<? extends T> itemClass,
                                  @LayoutRes int overrideItemLayoutResource,
                                  @NonNull Binder<? super T, ? extends View> binder) {
        return bind(itemClass, overrideItemLayoutResource, binder, ALWAYS);
    }

    /**
     * Map an item type to the specified binder.
     * <p>
     * In addition, this method accepts a {@link Predicate} that will be evaluated per-instance to determine if the
     * specified binder is suitable for use with the item.
     * @param itemClass The type of item accepted by the specified binder.
     * @param binder The binder to be used to bind the specified item type.
     * @param predicate A predicate that will be evaluated for each item instance to determine if the specified binder
     * is suitable.
     * @param <T> The type of item object.
     * @return This builder, to allow chaining.
     */
    @NonNull
    public <T> MapperBuilder bind(@NonNull Class<? extends T> itemClass,
                                  @NonNull Binder<? super T, ? extends View> binder,
                                  @NonNull Predicate<? super T> predicate) {
        return bind(itemClass, 0, binder, predicate);
    }

    /**
     * Map an item type to the specified binder.
     * @param itemClass The type of item accepted by the specified binder.
     * @param binder The binder to be used to bind the specified item type.
     * @param <T> The type of item object.
     * @return This builder, to allow chaining.
     */
    @NonNull
    public <T> MapperBuilder bind(@NonNull Class<? extends T> itemClass,
                                  @NonNull Binder<? super T, ? extends View> binder) {
        return bind(itemClass, 0, binder, ALWAYS);
    }

    /**
     * Allows overriding whether or not the resulting {@link Mapper} will report as having stable IDs.
     * @param stableIds {@code true} forcefully enables stable IDs, {@code false} forcefully disables them, {@code null}
     * falls back to the default behaviour of {@link AbstractMapper#hasStableIds()}.
     * @return This builder, to allow chaining.
     * @see Mapper#hasStableIds()
     * @see AbstractMapper#hasStableIds()
     * @see PowerAdapter#hasStableIds()
     */
    @NonNull
    public MapperBuilder stableIds(@Nullable Boolean stableIds) {
        mStableIds = stableIds;
        return this;
    }

    @NonNull
    public Mapper build() {
        return new RuleMapper(new HashMap<>(mRules), mStableIds);
    }

    private static final class RuleMapper extends AbstractMapper {

        @NonNull
        private final Map<Class<?>, List<Rule<?>>> mRules;

        @NonNull
        private final Set<Binder<?, ?>> mAllBinders = new HashSet<>();

        @Nullable
        private final Boolean mStableIds;

        RuleMapper(@NonNull Map<Class<?>, List<Rule<?>>> rules, @Nullable Boolean stableIds) {
            mRules = rules;
            mStableIds = stableIds;
            for (List<Rule<?>> list : rules.values()) {
                for (Rule rule : list) {
                    mAllBinders.add(rule.binder);
                }
            }
        }

        @Nullable
        @Override
        public Binder<?, ?> getBinder(@NonNull Object item, int position) {
            // Apply rules in order for most specific type first,
            // before moving up class hierarchy and applying those rules.
            Class<?> itemClass = item.getClass();
            while (itemClass != null) {
                List<Rule<?>> rules = mRules.get(itemClass);
                if (rules != null) {
                    for (int i = 0; i < rules.size(); i++) {
                        //noinspection unchecked
                        Rule<Object> rule = (Rule<Object>) rules.get(i);
                        if (rule.predicate.apply(item)) {
                            return rule.binder;
                        }
                    }
                }
                itemClass = itemClass.getSuperclass();
            }
            return null;
        }

        @NonNull
        @Override
        public Collection<? extends Binder<?, ?>> getAllBinders() {
            return mAllBinders;
        }

        @Override
        public boolean hasStableIds() {
            if (mStableIds != null) {
                return mStableIds;
            }
            return super.hasStableIds();
        }
    }

    private static final class Rule<T> {

        @NonNull
        final Predicate<? super T> predicate;

        @NonNull
        final Binder<? super T, ?> binder;

        Rule(@NonNull Predicate<? super T> predicate, @NonNull Binder<? super T, ?> binder) {
            this.predicate = predicate;
            this.binder = binder;
        }
    }
}
