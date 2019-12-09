# Remodular

A Clojure(Script) library for handling state.

## Objectives

- One state
- Purity
- Simplicity
- No hidden state (subscriptionsm, closures, etc)
- Dynamic modularity
- Data > Functions > Everything else

## Motivation

One of the great things about ClojureScript is how increadibly straight forward state handling is - you create an Atom at the top of the app and add one subscription that renders the app. React and quick comparison of Clojure's datastructures takes care of the rest. To alter state you just inject the atom and swap it with pure functions in response to user events or network activity.

So why a state handling library?

The main eyesore of the above is that the mutable Atom is injected into the app. That severly reduces the ammount of pure code in the app and if you use a single state (as you should) it gives the entire app the power to mutate any part. So we switched the Atom for a callback that we called trigger-event. trigger-event would work much like a reducer does in redux and translate an event to changes to the state. This enabled top-level control of all mutations of the app and we had once again reduced the impure mutations to the very top of the app.

But then we wanted modules — parts of the app that conceptually have their own state and state transitions. Doing this while staying true to all the above objectives proved to be a journey riddled with gotchas, many of which were unpleasant and that you'd probably prefer not to work through. Remodular is currently pretty far along that journey and the resulting architecture and functions aiding that architecture is capable enough to power at least one production-level application.

### Why another state handling library when we have reframe

//TODO

## The parts

### State Paths

```[:stack-navigator :route 3 :state :child-modules :chat]```

*An example state path to a chat module nested within a page in a stack-navigator view in an app.*

State paths are human-readable pointers into the single state of the application. Like URLs they can be absolute or relative. Within Remodular relative state paths are used to point out where a module keeps the state for any child modules that it has and absolute state paths are used to make deep edits of the state at the top of the app.

Generally a module does not receive or know the structure of the state of its parent. Therefore URL constructs like `/../folder/file` have not been necessary so far. Alterations of parent state should instead be done through events.

### Props

A module can receive an arbitrary number of props. These may be thought of as React props but React is not necessary and props are used outside UI rendering as well.

There are two props that will always be passed to a module, state and module-context.

**State** is the state of the module as extracted from the state of the entire application. This is immutable but can be altered (if permitted by parents and the app) by triggering events.

## Usage

FIXME

## The naïve paths traveled



## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
