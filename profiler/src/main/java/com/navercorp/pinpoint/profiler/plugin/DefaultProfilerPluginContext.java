/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.profiler.plugin;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.NotFoundInstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.PinpointInstrument;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matcher;
import com.navercorp.pinpoint.bootstrap.instrument.matcher.Matchers;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.PinpointClassFileTransformer;
import com.navercorp.pinpoint.bootstrap.interceptor.group.InterceptorGroup;
import com.navercorp.pinpoint.bootstrap.plugin.ApplicationTypeDetector;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;
import com.navercorp.pinpoint.exception.PinpointException;
import com.navercorp.pinpoint.profiler.DefaultAgent;
import com.navercorp.pinpoint.profiler.instrument.ClassInjector;
import com.navercorp.pinpoint.profiler.interceptor.group.DefaultInterceptorGroup;
import com.navercorp.pinpoint.profiler.plugin.xml.transformer.ClassFileTransformerBuilder;
import com.navercorp.pinpoint.profiler.plugin.xml.transformer.DefaultClassFileTransformerBuilder;
import com.navercorp.pinpoint.profiler.plugin.xml.transformer.MatchableClassFileTransformer;
import com.navercorp.pinpoint.profiler.util.JavaAssistUtils;
import com.navercorp.pinpoint.profiler.util.NameValueList;

public class DefaultProfilerPluginContext implements ProfilerPluginSetupContext, PinpointInstrument {
    private final DefaultAgent agent;
    private final ClassInjector classInjector;
    
    private final List<ApplicationTypeDetector> serverTypeDetectors = new ArrayList<ApplicationTypeDetector>();
    private final List<ClassFileTransformer> classTransformers = new ArrayList<ClassFileTransformer>();
    
    private final NameValueList<InterceptorGroup> interceptorGroups = new NameValueList<InterceptorGroup>();
    
    private boolean initialized = false;
    
    public DefaultProfilerPluginContext(DefaultAgent agent, ClassInjector classInjector) {
        this.agent = agent;
        this.classInjector = classInjector;
    }

    public ClassFileTransformerBuilder getClassFileTransformerBuilder(String targetClassName) {
        return new DefaultClassFileTransformerBuilder(this, targetClassName);
    }
    
    public void addClassFileTransformer(ClassFileTransformer transformer) {
        if (initialized) {
            throw new IllegalStateException("Context already initialized");
        }

        classTransformers.add(transformer);
    }

    @Override
    public ProfilerConfig getConfig() {
        return agent.getProfilerConfig();
    }

    @Override
    public TraceContext getTraceContext() {
        TraceContext context = agent.getTraceContext();
        
        if (context == null) {
            throw new IllegalStateException("TraceContext is not created yet");
        }
        
        return context;
    }
        
    @Override
    public void addApplicationTypeDetector(ApplicationTypeDetector... detectors) {
        if (initialized) {
            throw new IllegalStateException("Context already initialized");
        }
        
        for (ApplicationTypeDetector detector : detectors) {
            serverTypeDetectors.add(detector);
        }
    }
    
    @Override
    public InstrumentClass getInstrumentClass(ClassLoader classLoader, String className, byte[] classFileBuffer) {
        try {
            return agent.getClassPool().getClass(this, classLoader, className, classFileBuffer);
        } catch (NotFoundInstrumentException e) {
            return null;
        }
    }
    
    @Override
    public boolean exist(ClassLoader classLoader, String className) {
        return agent.getClassPool().hasClass(classLoader, className);
    }

    @Override
    public void addClassFileTransformer(final String targetClassName, final PinpointClassFileTransformer transformer) {
        if (initialized) {
            throw new IllegalStateException("Context already initialized");
        }

        classTransformers.add(new MatchableClassFileTransformer() {
            private final Matcher matcher = Matchers.newClassNameMatcher(JavaAssistUtils.javaNameToJvmName(targetClassName));
            
            @Override
            public Matcher getMatcher() {
                return matcher;
            }
            
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                try {
                    return transformer.transform(DefaultProfilerPluginContext.this, loader, targetClassName, classBeingRedefined, protectionDomain, classfileBuffer);
                } catch (InstrumentException e) {
                    throw new PinpointException(e);
                }
            }
        });
    }
    
    @Override
    public void addClassFileTransformer(ClassLoader classLoader, String targetClassName, final PinpointClassFileTransformer transformer) {
        agent.getDynamicTransformService().addClassFileTransformer(classLoader, targetClassName, new ClassFileTransformer() {

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                try {
                    return transformer.transform(DefaultProfilerPluginContext.this, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
                } catch (InstrumentException e) {
                    throw new PinpointException(e);
                }
            }
            
        });
    }

    @Override
    public void retransform(Class<?> target, final PinpointClassFileTransformer transformer) {
        agent.getDynamicTransformService().retransform(target, new ClassFileTransformer() {

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                try {
                    return transformer.transform(DefaultProfilerPluginContext.this, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
                } catch (InstrumentException e) {
                    throw new PinpointException(e);
                }
            }
            
        });
    }
    
    public <T> Class<? extends T> injectClass(ClassLoader targetClassLoader, String className) {
        return classInjector.injectClass(targetClassLoader, className);
    }

    public List<ClassFileTransformer> getClassEditors() {
        return classTransformers;
    }

    public List<ApplicationTypeDetector> getApplicationTypeDetectors() {
        return serverTypeDetectors;
    }

    @Override
    public InterceptorGroup getInterceptorGroup(String name) {
        InterceptorGroup group = interceptorGroups.get(name);
        
        if (group == null) {
            group = new DefaultInterceptorGroup(name);
            interceptorGroups.add(name, group);
        }
        
        return group;
    }
    
    public void markInitialized() {
        this.initialized = true;
    }
}
