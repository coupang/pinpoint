package com.navercorp.pinpoint.plugin.jdk.http;
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

import java.security.ProtectionDomain;

import com.navercorp.pinpoint.bootstrap.instrument.InstrumentClass;
import com.navercorp.pinpoint.bootstrap.instrument.InstrumentException;
import com.navercorp.pinpoint.bootstrap.instrument.PinpointInstrument;
import com.navercorp.pinpoint.bootstrap.instrument.transformer.PinpointClassFileTransformer;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin;
import com.navercorp.pinpoint.bootstrap.plugin.ProfilerPluginSetupContext;

/**
 * 
 * @author Jongho Moon
 *
 */
public class JdkHttpPlugin implements ProfilerPlugin {

    @Override
    public void setup(ProfilerPluginSetupContext context) {
        context.addClassFileTransformer("sun.net.www.protocol.http.HttpURLConnection", new PinpointClassFileTransformer() {
            
            @Override
            public byte[] transform(PinpointInstrument instrumentContext, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws InstrumentException {
                InstrumentClass target = instrumentContext.getInstrumentClass(loader, className, classfileBuffer);
                
                target.addGetter(ConnectedGetter.class.getName(), "connected");

                if (target.hasField("connecting", "boolean")) {
                    target.addGetter(ConnectingGetter.class.getName(), "connecting");
                }
                
                target.addInterceptor("com.navercorp.pinpoint.plugin.jdk.http.interceptor.HttpURLConnectionInterceptor");
                
                return target.toBytecode();
            }
        });
    }

}
