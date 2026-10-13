package org.protege.editor.owl.model.io.jsonld;

import org.protege.editor.core.prefs.Preferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Preferences} store that keeps values in memory, so tests never touch the user's real Protégé preferences.
 */
public class InMemoryPreferences implements Preferences {

    private final Map<String, Object> values = new HashMap<>();

    @Override
    public void clear() {
        values.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, T def) {
        Object value = values.get(key);
        return value == null ? def : (T) value;
    }

    @Override
    public String getString(String key, String def) { return get(key, def); }

    @Override
    public void putString(String key, String val) { values.put(key, val); }

    @Override
    public List<String> getStringList(String key, List<String> def) { return get(key, def); }

    @Override
    public void putStringList(String key, List<String> val) { values.put(key, new ArrayList<>(val)); }

    @Override
    public int getInt(String key, int def) { return get(key, def); }

    @Override
    public void putInt(String key, int val) { values.put(key, val); }

    @Override
    public float getFloat(String key, float def) { return get(key, def); }

    @Override
    public void putFloat(String key, float val) { values.put(key, val); }

    @Override
    public long getLong(String key, long def) { return get(key, def); }

    @Override
    public void putLong(String key, long val) { values.put(key, val); }

    @Override
    public double getDouble(String key, double def) { return get(key, def); }

    @Override
    public void putDouble(String key, double val) { values.put(key, val); }

    @Override
    public boolean getBoolean(String key, boolean def) { return get(key, def); }

    @Override
    public void putBoolean(String key, boolean val) { values.put(key, val); }

    @Override
    public byte[] getByteArray(String key, byte[] def) { return get(key, def); }

    @Override
    public void putByteArray(String key, byte[] val) { values.put(key, val); }

    @Override
    public List<byte[]> getByteArrayList(String key, List<byte[]> def) { return get(key, def); }

    @Override
    public void putByteArrayList(String key, List<byte[]> val) { values.put(key, new ArrayList<>(val)); }
}
