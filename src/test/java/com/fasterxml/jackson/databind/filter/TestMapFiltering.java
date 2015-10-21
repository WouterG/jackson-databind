package com.fasterxml.jackson.databind.filter;

import java.io.IOException;
import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@SuppressWarnings("serial")
public class TestMapFiltering extends BaseMapTest
{
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomOffset
    {
        public int value();
    }

    @JsonFilter("filterForMaps")
    static class FilteredBean extends LinkedHashMap<String,Integer> { }
    
    static class MapBean {
        @JsonFilter("filterX")
        @CustomOffset(1)
        public Map<String,Integer> values;
        
        public MapBean() {
            values = new LinkedHashMap<String,Integer>();
            values.put("a", 1);
            values.put("b", 5);
            values.put("c", 9);
        }
    }

    static class MyMapFilter implements PropertyFilter
    {
        @Override
        public void serializeAsField(Object value, JsonGenerator jgen,
                SerializerProvider provider, PropertyWriter writer)
            throws Exception
        {
            String name = writer.getName();
            if (!"a".equals(name)) {
                return;
            }
            CustomOffset n = writer.findAnnotation(CustomOffset.class);
            int offset = (n == null) ? 0 : n.value();
            Integer I = offset + ((Integer) value).intValue();

            writer.serializeAsField(I, jgen, provider);
        }

        @Override
        public void serializeAsElement(Object elementValue, JsonGenerator jgen,
                SerializerProvider prov, PropertyWriter writer)
                throws Exception {
            // not needed for testing
        }

        @Override
        @Deprecated
        public void depositSchemaProperty(PropertyWriter writer,
                ObjectNode propertiesNode, SerializerProvider provider)
                throws JsonMappingException {
            
        }

        @Override
        public void depositSchemaProperty(PropertyWriter writer,
                JsonObjectFormatVisitor objectVisitor,
                SerializerProvider provider) throws JsonMappingException {
        }
    }

    // [databind#527]
    static class NoNullValuesMapContainer {
        @JsonInclude(content=JsonInclude.Include.NON_NULL)
        public Map<String,String> stuff = new LinkedHashMap<String,String>();
        
        public NoNullValuesMapContainer add(String key, String value) {
            stuff.put(key, value);
            return this;
        }
    }
    
    // [databind#527]
    @JsonInclude(content=JsonInclude.Include.NON_NULL)
    static class NoNullsStringMap extends LinkedHashMap<String,String> {
        public NoNullsStringMap add(String key, String value) {
            put(key, value);
            return this;
        }
    }

    // [databind#527]
    @JsonInclude(content=JsonInclude.Include.NON_ABSENT)
    static class NoAbsentStringMap extends LinkedHashMap<String, AtomicReference<?>> {
        public NoAbsentStringMap add(String key, Object value) {
            put(key, new AtomicReference<Object>(value));
            return this;
        }
    }
    
    // [databind#527]
    @JsonInclude(content=JsonInclude.Include.NON_EMPTY)
    static class NoEmptyStringsMap extends LinkedHashMap<String,String> {
        public NoEmptyStringsMap add(String key, String value) {
            put(key, value);
            return this;
        }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    final ObjectMapper MAPPER = objectMapper();
    
    public void testMapFilteringViaProps() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider().addFilter("filterX",
                SimpleBeanPropertyFilter.filterOutAllExcept("b"));
        String json = MAPPER.writer(prov).writeValueAsString(new MapBean());
        assertEquals(aposToQuotes("{'values':{'b':5}}"), json);
    }

    public void testMapFilteringViaClass() throws Exception
    {
        FilteredBean bean = new FilteredBean();
        bean.put("a", 4);
        bean.put("b", 3);
        FilterProvider prov = new SimpleFilterProvider().addFilter("filterForMaps",
                SimpleBeanPropertyFilter.filterOutAllExcept("b"));
        String json = MAPPER.writer(prov).writeValueAsString(bean);
        assertEquals(aposToQuotes("{'b':3}"), json);
    }

    // [databind#527]
    public void testNonNullValueMapViaProp() throws IOException
    {
        String json = MAPPER.writeValueAsString(new NoNullValuesMapContainer()
            .add("a", "foo")
            .add("b", null)
            .add("c", "bar"));
        assertEquals(aposToQuotes("{'stuff':{'a':'foo','c':'bar'}}"), json);
    }
    
    // [databind#522]
    public void testMapFilteringWithAnnotations() throws Exception
    {
        FilterProvider prov = new SimpleFilterProvider().addFilter("filterX",
                new MyMapFilter());
        String json = MAPPER.writer(prov).writeValueAsString(new MapBean());
        // a=1 should become a=2
        assertEquals(aposToQuotes("{'values':{'a':2}}"), json);
    }

    // [databind#527]
    public void testMapNonNullValue() throws IOException
    {
        String json = MAPPER.writeValueAsString(new NoNullsStringMap()
            .add("a", "foo")
            .add("b", null)
            .add("c", "bar"));
        assertEquals(aposToQuotes("{'a':'foo','c':'bar'}"), json);
    }

    // [databind#527]
    public void testMapNonEmptyValue() throws IOException
    {
        String json = MAPPER.writeValueAsString(new NoEmptyStringsMap()
            .add("a", "foo")
            .add("b", "bar")
            .add("c", ""));
        assertEquals(aposToQuotes("{'a':'foo','b':'bar'}"), json);
    }

    // Test to ensure absent content of AtomicReference handled properly
    // [databind#527]
    public void testMapAbsentValue() throws IOException
    {
        String json = MAPPER.writeValueAsString(new NoAbsentStringMap()
            .add("a", "foo")
            .add("b", null));
        assertEquals(aposToQuotes("{'a':'foo'}"), json);
    }

    public void testMapNullSerialization() throws IOException
    {
        ObjectMapper m = new ObjectMapper();
        Map<String,String> map = new HashMap<String,String>();
        map.put("a", null);
        // by default, should output null-valued entries:
        assertEquals("{\"a\":null}", m.writeValueAsString(map));
        // but not if explicitly asked not to (note: config value is dynamic here)
        m.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        assertEquals("{}", m.writeValueAsString(map));
    }
}