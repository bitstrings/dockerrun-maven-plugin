package org.bitstrings.maven.plugins.dockerrun.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Data<T>
    implements Map<String, T>
{
    private final Map<String, T> data = Collections.synchronizedMap(new LinkedHashMap<>());

    public List<String> asList()
    {
        return Collections.unmodifiableList(new ArrayList<>(data.keySet()));
    }

    @Override
    public void clear()
    {
        data.clear();
    }

    @Override
    public T compute(String key, BiFunction<? super String, ? super T, ? extends T> remappingFunction)
    {
        return data.compute(key, remappingFunction);
    }

    @Override
    public T computeIfAbsent(String key, Function<? super String, ? extends T> mappingFunction)
    {
        return data.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public T computeIfPresent(String key, BiFunction<? super String, ? super T, ? extends T> remappingFunction)
    {
        return data.computeIfPresent(key, remappingFunction);
    }

    @Override
    public boolean containsKey(Object key)
    {
        return data.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return data.containsValue(value);
    }

    @Override
    public Set<Entry<String, T>> entrySet()
    {
        return data.entrySet();
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super T> action)
    {
        data.forEach(action);
    }

    @Override
    public T get(Object key)
    {
        return data.get(key);
    }

    @Override
    public T getOrDefault(Object key, T defaultValue)
    {
        return data.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean isEmpty()
    {
        return data.isEmpty();
    }

    @Override
    public Set<String> keySet()
    {
        return data.keySet();
    }

    @Override
    public T merge(String key, T value, BiFunction<? super T, ? super T, ? extends T> remappingFunction)
    {
        return data.merge(key, value, remappingFunction);
    }

    @Override
    public T put(String key, T value)
    {
        return data.put(key, value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m)
    {
        data.putAll(m);
    }

    @Override
    public T putIfAbsent(String key, T value)
    {
        return data.putIfAbsent(key, value);
    }

    @Override
    public T remove(Object key)
    {
        return data.remove(key);
    }

    @Override
    public boolean remove(Object key, Object value)
    {
        return data.remove(key, value);
    }

    @Override
    public boolean replace(String key, T oldValue, T newValue)
    {
        return data.replace(key, oldValue, newValue);
    }

    @Override
    public T replace(String key, T value)
    {
        return data.replace(key, value);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super T, ? extends T> function)
    {
        data.replaceAll(function);
    }

    @Override
    public int size()
    {
        return data.size();
    }

    @Override
    public Collection<T> values()
    {
        return data.values();
    }
}
