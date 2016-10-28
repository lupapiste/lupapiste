/*
 * Assignments for application
 *
 */
LUPAPISTE.AssignmentService = function() {
  "use strict";
  var self = this;

   // {:id              ssc/ObjectIdStr
   // :organization-id sc/Str
   // :application-id  sc/Str
   // :target          sc/Any
   // :created         ssc/Timestamp
   // :creator-id      ssc/ObjectIdStr
   // :recipient-id    ssc/ObjectIdStr
   // :completed       (sc/maybe ssc/Timestamp)
   // :completer-id    (sc/maybe sc/Str)
   // :active          sc/Bool
   // :description     sc/Str})

  var _data = ko.observableArray([]);

  self.assignments = ko.pureComputed(function() {
    return _data();
  });

  /*
   * Targets are two levels deep and represented as objects.
   * Keys are the "target groups", and value for each key is array of corresponding items in application.
   * Example with only parties group:
   * {"parties": [{id: "5808c517f16562feee6856fb", displayText: "Paasuunnittelija"},
                  {id: "5808c517f16562feee6856fc", displayText: "Suunnittelija"}]}
   */
  self.targets = ko.observableArray([]);

  function assignmentTargetsQuery(id) {
    ajax.query("assignment-targets", {id: id, lang: loc.getCurrentLanguage()})
      .success(function(resp) {
        self.targets(_.fromPairs(resp.targets));
      })
      .call();
  }

  function assignmentsForApplication(id) {
    ajax.query("assignments-for-application", {id: id})
      .success(function(resp) {
        _data(resp.assignments);
      })
      .call();
  }

  hub.subscribe("assignmentService::createAssignment", function(event) {
    ajax.command("create-assignment", _.omit(event, "eventType"))
     .success(function(resp) {
      util.showSavedIndicator(resp);
      // Refresh application assignments
      assignmentsForApplication(event.id);
     })
     .call();
  });

  function onAssignmentCompleted(response) {
    util.showSavedIndicator(response);
    hub.send("assignmentService::assignmentCompleted", null);
  }

  hub.subscribe("assignmentService::markComplete", function(event) {
    ajax.command("complete-assignment", {assignmentId: _.get(event, "assignmentId")})
      .success(onAssignmentCompleted)
      .call();
  });

  hub.subscribe("assignmentService::targetsQuery", function(event) {
    assignmentTargetsQuery(_.get(event, "applicationId"));
  });

  hub.subscribe("assignmentService::applicationAssignments", function(event) {
    assignmentsForApplication(_.get(event, "applicationId"));
  });

  hub.subscribe("application-model-updated", function(event) {
    assignmentsForApplication(_.get(event, "applicationId"));
  });


};
