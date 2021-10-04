package org.orecruncher.dsurround.lib.validation;

import java.util.Map;

@SuppressWarnings("unused")
public class MapValidator<K, V extends IValidator<V>> implements IValidator<Map<K, V>> {

    @Override
    public void validate(final Map<K, V> obj) throws ValidationException {
        for (final Map.Entry<K, V> kvp : obj.entrySet()) {
            final V val = kvp.getValue();
            val.validate(val);
        }
    }
}