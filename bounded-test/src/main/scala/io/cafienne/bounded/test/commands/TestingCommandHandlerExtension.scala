// Copyright (C) 2018 the original author or authors.
// See the LICENSE file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.cafienne.bounded.test.commands

import io.cafienne.bounded.akka.{AggregateRoot, CommandHandling}
import io.cafienne.bounded.commands.{AggregateRootEvent, AggregateRootState}

case object GetState
case object NoState extends AggregateRootState

/**
  * Chaining trait that extends the CommandHandling Persistent Actor
  * to allow storage of events directly
  * and to fetch the internal state of the aggregate root.
  */
trait TestingCommandHandlerExtension extends AggregateRoot with CommandHandling {
  commandHandler {
    case m: AggregateRootEvent =>
      persist(m) { e =>
        sender() ! e
    }
    case _: GetState.type =>
      state.fold(sender() ! NoState)(state => sender() ! state)
  }
}