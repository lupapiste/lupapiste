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
   var test = {id: "123", organizationId: "753-R", applicationId:"LP-753-2016-00001",
               target: ["documents", "parties", "1234"], created: 1476772272398,
               creator: {id: "321", username: "pena@example.com"},
               recipient: {id: "4123", username: "sonja"},
               status: "active", description: "FOOFAA"};

  var _data = ko.observableArray([test]);

  self.assignments = ko.pureComputed(function() {
    return _data();
  });


  hub.subscribe("assignmentService::createAssignment", function(event) {
    ajax.command("create-assignment", _.omit(event, "eventType"))
     .success(util.showSavedIndicator)
     .call();
  });

  hub.subscribe("assignmentService::markComplete", function() {
    // ajax.command("complete-assignment", _.get(event, "assignmentId"))
    // .success(util.showSavedIndicator)
    // .call();
  });

};
