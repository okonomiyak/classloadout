package uk.iwaservice.classloadout.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import javax.annotation.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Soft Item Zoomer integration. It has no public provider API, so a Proxy of its HoveredStackProvider is appended
 * to HoveredStackProviderRegistry's private "providers" list on first use. Any failure leaves this a no-op.
 */
public final class ItemZoomerCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean tried;
    private static Screen screen;
    @Nullable private static ItemStack stack;
    private static Rect2i bounds;

    /** Call once per frame from a screen's render(), with the hovered stack (null = nothing) and the screen's panel rect. */
    public static void hover(Screen s, @Nullable ItemStack hovered, int x, int y, int w, int h) {
        if (!tried) {
            tried = true;
            register();
        }
        screen = s;
        stack = hovered;
        bounds = new Rect2i(x, y, w, h);
    }

    @SuppressWarnings("unchecked")
    private static void register() {
        if (!ModList.get().isLoaded("itemzoomer")) {
            return;
        }
        try {
            Class<?> iface = Class.forName("com.imeetake.itemzoomer.compat.HoveredStackProvider");
            Class<?> registry = Class.forName("com.imeetake.itemzoomer.compat.HoveredStackProviderRegistry");
            Object provider = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, m, args) -> switch (m.getName()) {
                case "getHoveredStack" -> Minecraft.getInstance().screen == screen ? stack : null;
                case "getWindowBounds" -> Minecraft.getInstance().screen == screen ? bounds : null;
                case "getExclusionBounds" -> List.of();
                case "shouldDeferAbove" -> false;
                case "isAvailable" -> true;
                case "getModName" -> "classloadout";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "classloadout ItemZoomer provider";
                default -> null;
            });
            Field f = registry.getDeclaredField("providers");
            f.setAccessible(true);
            ((List<Object>) f.get(null)).add(provider);
        } catch (Throwable t) {
            LOGGER.warn("Item Zoomer integration unavailable", t);
        }
    }

    private ItemZoomerCompat() {}
}
