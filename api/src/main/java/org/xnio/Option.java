/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A strongly-typed option to configure an aspect of a service or connection.  Options are immutable and use identity comparisons
 * and hash codes.  Options should always be declared as <code>public static final</code> members in order to support serialization.
 *
 * @param <T> the option value type
 */
public abstract class Option<T> implements Serializable {

    private static final long serialVersionUID = -1564427329140182760L;

    private final Class<?> declClass;
    private final String name;

    Option(final Class<?> declClass, final String name) {
        this.declClass = declClass;
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        this.name = name;
    }

    /**
     * Create an option with a simple type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param type the class of the value associated with this option
     * @return the option instance
     */
    public static <T> Option<T> simple(final Class<?> declClass, final String name, final Class<T> type) {
        return new SingleOption<T>(declClass, name, type);
    }

    /**
     * Create an option with a sequence type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param elementType the class of the sequence element value associated with this option
     * @return the option instance
     */
    public static <T> Option<Sequence<T>> sequence(final Class<?> declClass, final String name, final Class<T> elementType) {
        return new SequenceOption<T>(declClass, name, elementType);
    }

    /**
     * Create an option with a class type.  The class object given may represent any type.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param declType the class object for the type of the class object given
     * @param <T> the type of the class object given
     * @return the option instance
     */
    public static <T> Option<Class<? extends T>> type(final Class<?> declClass, final String name, final Class<T> declType) {
        return new TypeOption<T>(declClass, name, declType);
    }

    /**
     * Create an option with a sequence-of-types type.  The class object given may represent any type.
     *
     * @param declClass the declaring class of the option
     * @param name the (field) name of this option
     * @param elementDeclType the class object for the type of the sequence element class object given
     * @param <T> the type of the sequence element class object given
     * @return the option instance
     */
    public static <T> Option<Sequence<Class<? extends T>>> typeSequence(final Class<?> declClass, final String name, final Class<T> elementDeclType) {
        return new TypeSequenceOption<T>(declClass, name, elementDeclType);
    }

    /**
     * Get the name of this option.
     *
     * @return the option name
     */
    public String getName() {
        return name;
    }

    /**
     * Get a human-readable string representation of this object.
     *
     * @return the string representation
     */
    public String toString() {
        return declClass.getName() + "." + name;
    }

    /**
     * Get an option from a string name, using the given classloader.  If the classloader is {@code null}, the bootstrap
     * classloader will be used.
     *
     * @param name the option string
     * @param classLoader the class loader
     * @return the option
     * @throws IllegalArgumentException if the given option name is not valid
     */
    public static Option<?> fromString(String name, ClassLoader classLoader) throws IllegalArgumentException {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot == -1) {
            throw new IllegalArgumentException("Invalid option name");
        }
        final String fieldName = name.substring(lastDot + 1);
        final String className = name.substring(0, lastDot);
        try {
            final Field field = Class.forName(className, true, classLoader).getField(fieldName);
            final int modifiers = field.getModifiers();
            if (! Modifier.isPublic(modifiers)) {
                throw new IllegalArgumentException("Invalid Option instance (the field is not public)");
            }
            if (! Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException("Invalid Option instance (the field is not static)");
            }
            final Option<?> option = (Option<?>) field.get(null);
            if (option == null) {
                throw new IllegalArgumentException("Invalid null Option");
            }
            return option;
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No such field");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Illegal access", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class '" + className + "' not found");
        }
    }

    /**
     * Return the given object as the type of this option.  If the cast could not be completed, an exception is thrown.
     *
     * @param o the object to cast
     * @return the cast object
     * @throws ClassCastException if the object is not of a compatible type
     */
    public abstract T cast(Object o) throws ClassCastException;

    /**
     * Parse a string value for this option.
     *
     * @param string the string
     * @param classLoader the class loader to use to parse the value
     * @return the parsed value
     * @throws IllegalArgumentException if the argument could not be parsed
     */
    public abstract T parseValue(String string, ClassLoader classLoader) throws IllegalArgumentException;

    /**
     * Resolve this instance for serialization.
     *
     * @return the resolved object
     * @throws java.io.ObjectStreamException if the object could not be resolved
     */
    protected final Object readResolve() throws ObjectStreamException {
        try {
            final Field field = declClass.getField(name);
            final int modifiers = field.getModifiers();
            if (! Modifier.isPublic(modifiers)) {
                throw new InvalidObjectException("Invalid Option instance (the field is not public)");
            }
            if (! Modifier.isStatic(modifiers)) {
                throw new InvalidObjectException("Invalid Option instance (the field is not static)");
            }
            final Option<?> option = (Option<?>) field.get(null);
            if (option == null) {
                throw new InvalidObjectException("Invalid null Option");
            }
            return option;
        } catch (NoSuchFieldException e) {
            throw new InvalidObjectException("Invalid Option instance (no matching field)");
        } catch (IllegalAccessException e) {
            throw new InvalidObjectException("Invalid Option instance (Illegal access on field get)");
        }
    }

    /**
     * Create a builder for an immutable option set.
     *
     * @return the builder
     */
    public static Option.SetBuilder setBuilder() {
        return new Option.SetBuilder();
    }

    /**
     * A builder for an immutable option set.
     */
    public static class SetBuilder {
        private List<Option<?>> optionSet = new ArrayList<Option<?>>();

        SetBuilder() {
        }

        /**
         * Add an option to this set.
         *
         * @param option the option to add
         * @return this builder
         */
        public Option.SetBuilder add(Option<?> option) {
            if (option == null) {
                throw new NullPointerException("option is null");
            }
            optionSet.add(option);
            return this;
        }

        /**
         * Add all options from a collection to this set.
         *
         * @param options the options to add
         * @return this builder
         */
        public Option.SetBuilder addAll(Collection<Option<?>> options) {
            if (options == null) {
                throw new NullPointerException("options is null");
            }
            for (Option<?> option : options) {
                add(option);
            }
            return this;
        }

        /**
         * Create the immutable option set instance.
         *
         * @return the option set
         */
        public Set<Option<?>> create() {
            return Collections.unmodifiableSet(new LinkedHashSet<Option<?>>(optionSet));
        }
    }

    interface ValueParser<T> {
        T parseValue(String string, ClassLoader classLoader) throws IllegalArgumentException;
    }

    private static final Map<Class<?>, Option.ValueParser<?>> parsers;

    private static final Option.ValueParser<?> noParser = new Option.ValueParser<Object>() {
        public Object parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
            throw new IllegalArgumentException("No parser for this value type");
        }
    };

    static {
        final Map<Class<?>, Option.ValueParser<?>> map = new HashMap<Class<?>, Option.ValueParser<?>>();
        map.put(Byte.class, new Option.ValueParser<Byte>() {
            public Byte parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Byte.decode(string.trim());
            }
        });
        map.put(Short.class, new Option.ValueParser<Short>() {
            public Short parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Short.decode(string.trim());
            }
        });
        map.put(Integer.class, new Option.ValueParser<Integer>() {
            public Integer parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Integer.decode(string.trim());
            }
        });
        map.put(Long.class, new Option.ValueParser<Long>() {
            public Long parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Long.decode(string.trim());
            }
        });
        map.put(String.class, new Option.ValueParser<String>() {
            public String parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return string.trim();
            }
        });
        map.put(Boolean.class, new Option.ValueParser<Boolean>() {
            public Boolean parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return Boolean.valueOf(string.trim());
            }
        });
        parsers = map;
    }

    static <T> Option.ValueParser<Class<? extends T>> getClassParser(final Class<T> argType) {
        return new ValueParser<Class<? extends T>>() {
            public Class<? extends T> parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                try {
                    return Class.forName(string, false, classLoader).asSubclass(argType);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Class '" + string + "' not found", e);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException("Class '" + string + "' is not an instance of " + argType);
                }
            }
        };
    }

    static <T> Option.ValueParser<T> getEnumParser(final Class<T> enumType) {
        return new ValueParser<T>() {
            public T parseValue(final String string, final ClassLoader classLoader) throws IllegalArgumentException {
                return enumType.cast(Enum.valueOf(enumType.asSubclass(Enum.class), string.trim()));
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <T> Option.ValueParser<T> getParser(final Class<T> argType) {
        if (argType.isEnum()) {
            return getEnumParser(argType);
        } else {
            final Option.ValueParser<?> value = parsers.get(argType);
            return (Option.ValueParser<T>) (value == null ? noParser : value);
        }
    }
}

