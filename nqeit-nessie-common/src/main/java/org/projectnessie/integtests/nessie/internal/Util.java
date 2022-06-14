/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.integtests.nessie.internal;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.projectnessie.integtests.nessie.NessieTestsExtension;

public class Util {

  static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(NessieTestsExtension.class);

  public static void checkSupportedParameterType(
      Class<? extends Annotation> annotation,
      ParameterContext parameterContext,
      Class<?>... supportedTypes) {
    Class<?> paramType = parameterContext.getParameter().getType();

    for (Class<?> type : supportedTypes) {
      if (type.isAssignableFrom(paramType)) {
        return;
      }
    }

    throw new ParameterResolutionException(
        String.format(
            "Parameter %s on %s.%s: annotation @%s does only support parameters types: %s.",
            parameterContext.getParameter().getName(),
            parameterContext.getDeclaringExecutable().getDeclaringClass().getName(),
            parameterContext.getDeclaringExecutable().getName(),
            annotation.getSimpleName(),
            Arrays.stream(supportedTypes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "))));
  }

  public static Map<String, String> nessieClientParams(ExtensionContext extensionContext) {
    Map<String, String> params = new HashMap<>();

    params.put("ref", DefaultBranchPerRun.get(extensionContext).getDefaultBranch().getName());
    params.put("uri", NessieEnv.get(extensionContext).getNessieUri());
    System.getProperties().entrySet().stream()
        .filter(e -> e.getKey().toString().startsWith("nessie.client."))
        .forEach(
            e ->
                params.put(
                    e.getKey().toString().substring("nessie.client.".length()),
                    e.getValue().toString()));
    return params;
  }
}
