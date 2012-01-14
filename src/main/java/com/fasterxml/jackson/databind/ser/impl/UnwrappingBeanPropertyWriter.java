package com.fasterxml.jackson.databind.ser.impl;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.*;

/**
 * Variant of {@link BeanPropertyWriter} which will handle unwrapping
 * of JSON Object (including of properties of Object within surrounding
 * JSON object, and not as sub-object).
 */
public class UnwrappingBeanPropertyWriter
    extends BeanPropertyWriter
{
    protected final String _prefix;
    
    public UnwrappingBeanPropertyWriter(BeanPropertyWriter base, String prefix) {
        super(base);
        _prefix = (prefix == null || prefix.length() == 0) ? null : prefix;
    }

    private UnwrappingBeanPropertyWriter(UnwrappingBeanPropertyWriter base, SerializedString name) {
        super(base);
        _prefix = base._prefix;
    }

    @Override
    public UnwrappingBeanPropertyWriter withName(String newName) {
        if (newName.equals(_name.toString())) {
            return this;
        }
        return new UnwrappingBeanPropertyWriter(this, new SerializedString(newName));
    }
    
    @Override
    public void serializeAsField(Object bean, JsonGenerator jgen, SerializerProvider prov)
        throws Exception
    {
        Object value = get(bean);
        if (value == null) {
            // Hmmh. I assume we MUST pretty much suppress nulls, since we
            // can't really unwrap them...
            return;
        }
        // For non-nulls, first: simple check for direct cycles
        if (value == bean) {
            _reportSelfReference(bean);
        }
        JsonSerializer<Object> ser = _serializer;
        if (ser == null) {
            Class<?> cls = value.getClass();
            PropertySerializerMap map = _dynamicSerializers;
            ser = map.serializerFor(cls);
            if (ser == null) {
                ser = _findAndAddDynamic(map, cls, prov);
            }
        }
        if (_suppressableValue != null) {
            if (MARKER_FOR_EMPTY == _suppressableValue) {
                if (ser.isEmpty(value)) {
                    return;
                }
            } else if (_suppressableValue.equals(value)) {
                return;
            }
        }

        // note: must verify we are using unwrapping serializer; if not, will write field name
        if (!ser.isUnwrappingSerializer()) {
            jgen.writeFieldName(_name);
        }

        if (_typeSerializer == null) {
            ser.serialize(value, jgen, prov);
        } else {
            ser.serializeWithType(value, jgen, prov, _typeSerializer);
        }
    }

    // need to override as we must get unwrapping instance...
    @Override
    protected JsonSerializer<Object> _findAndAddDynamic(PropertySerializerMap map,
            Class<?> type, SerializerProvider provider) throws JsonMappingException
    {
        JsonSerializer<Object> serializer;
        if (_nonTrivialBaseType != null) {
            JavaType subtype = provider.constructSpecializedType(_nonTrivialBaseType, type);
            serializer = provider.findValueSerializer(subtype, this);
        } else {
            serializer = provider.findValueSerializer(type, this);
        }
        if (!serializer.isUnwrappingSerializer()) {
            serializer = serializer.unwrappingSerializer(_prefix);
        }
        _dynamicSerializers = _dynamicSerializers.newWith(type, serializer);
        return serializer;
    }
}
