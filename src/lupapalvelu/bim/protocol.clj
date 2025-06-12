(ns lupapalvelu.bim.protocol
  "Protocol for hiding specific BIM services beneath a generic wrapper,
  specifically before and after sending or receiving a pubsub message.
  Here the methods are:
  *-topic     returns string pubsub topic this implementation uses (internal or bimmer)
  *-request   forms the body for the request pubsub message
  *-handler   does the integration request (Note: only if internal; otherwise bimmer handles the request)
  *-response  receives the result from the handler and finishes the integration process

  In detail the process works like this:
  - user uploads an IFC file; extraction is triggered automatically on upload, while
    checking and XKT generation may also be requested explicitly
    - backend sends a pubsub message with topic and contents determined by *-request
    - the request message is received and handled
      - if the topic was \"internal-bim-*-request\", it is handled by *-handler
      - else the handling is done outside lupapiste (in bimmer)
    - integration returns results which are turned into a pubsub response message
      - bimmer internally handles this
      - solibri has a webhook for this which it calls in lupapiste
    - *-response receives the message and finalizes the job
  - meanwhile frontend polls backend to update the status of the job until it is done

  See also `docs/bim-pubsub-process.puml`")

(defprotocol BIMExtraction
  "Data extraction integration implementation for getting document properties from an IFC file"
  (-extraction-request [self context])
  (-extraction-handler [self message])
  (-extraction-response [self message]))

(defprotocol BIMChecking
  "Compliance and validation checking implementation for checking the IFC file itself"
  (-checking-request [self context])
  (-checking-handler [self system message])
  (-checking-response [self message]))
