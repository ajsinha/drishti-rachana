/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.FunctionProvider;
import com.ash.rachana.el.Values;
import com.ash.rachana.profile.Profile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Split step A6: registries per instance; a profile adds, never redefines; a host rebuilds and swaps. */
class RachanaInstanceTest {

    private static FunctionProvider provider(String source, String name, Object answer) {
        return new FunctionProvider() {
            public String source() { return source; }
            public List<Provided> functions() {
                return List.of(new Provided(name, 0, 1, "", "measure", "a test function", "", (a, c) -> answer));
            }
        };
    }

    private static Object eval(Rachana r, String e) {
        return Values.simplify(r.compiler().compile(e).eval(EvalContext.of(Node.of(Map.of()), r.formats())));
    }

    @Test
    void twoInstancesDoNotSeeEachOthersFunctions() {
        Rachana a = Rachana.with(RachanaOptions.DEFAULTS, Profile.core(), Profile.named("one").functions(provider("one", "greet", "hello")).build());
        Rachana b = Rachana.with(RachanaOptions.DEFAULTS, Profile.named("two").functions(provider("two", "greet", "bonjour")).build());
        assertThat(eval(a, "greet()")).isEqualTo("hello");
        assertThat(eval(b, "greet()")).isEqualTo("bonjour");
        assertThatThrownBy(() -> eval(Rachana.core(), "greet()")).hasMessageContaining("unknown function 'greet'");
        assertThat(a.functions().providerOf("greet")).isEqualTo("one");
        assertThat(Rachana.core().functions().has("greet")).isFalse();
    }

    @Test
    void aProfileCannotRedefineACoreName() {
        Profile bad = Profile.named("bad").functions(provider("bad", "upper", "x")).build();
        assertThatThrownBy(() -> Rachana.with(null, bad)).isInstanceOf(IllegalStateException.class).hasMessageContaining("upper is Core's");
        assertThatThrownBy(() -> Profile.named("core")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNameTwoProfilesGiveIsRefused() {
        Profile one = Profile.named("one").functions(provider("one", "greet", "a")).build();
        Profile two = Profile.named("two").functions(provider("two", "greet", "b")).build();
        assertThatThrownBy(() -> Rachana.with(null, one, two)).hasMessageContaining("provided twice");
        assertThatThrownBy(() -> Rachana.with(null, one, one)).hasMessageContaining("given twice");
    }

    @Test
    void startStopRestartIsARebuildAndSwap() {
        Profile base = Profile.named("host").build();
        Rachana before = Rachana.with(null, base);
        Rachana started = Rachana.with(null, base, Profile.named("module").functions(provider("module", "greet", "hi")).build());
        assertThat(eval(started, "greet()")).isEqualTo("hi");
        Rachana stopped = Rachana.with(null, base);
        assertThatThrownBy(() -> eval(stopped, "greet()")).hasMessageContaining("unknown function");
        assertThatThrownBy(() -> eval(before, "greet()")).hasMessageContaining("unknown function");
        Rachana restarted = Rachana.with(null, base, Profile.named("module").functions(provider("module", "greet", "again")).build());
        assertThat(eval(restarted, "greet()")).isEqualTo("again");
        assertThat(eval(started, "greet()")).isEqualTo("hi");          // the old instance is untouched until dropped
    }

    @Test
    void regexCapsArePerInstance() {
        Rachana tight = Rachana.with(RachanaOptions.DEFAULTS.withRegexLimits(new com.ash.rachana.el.SafeRegex.Limits(3, 4000, 8192, 50_000_000L)));
        assertThat(eval(Rachana.core(), "matches('abcd', 'abcd')")).isEqualTo(true);
        assertThatThrownBy(() -> eval(tight, "matches('abcd', 'abcd')")).hasMessageContaining("limit is 3");
    }
}
