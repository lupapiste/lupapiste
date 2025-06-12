LUPAPISTE.ForemanAssignmentsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.assignmentService;

  self.foremanAssignments = self.disposedPureComputed( function() {
    return _.map( service.foremanAssignments(),
                  function( assi ) {
                    var text = "";
                    var firstName = _.trim( _.get( assi, "recipient.firstName"));
                    var lastName = _.trim( _.get( assi, "recipient.lastName" ));
                    if( firstName || lastName ) {
                      text += _.trim( firstName + " " + lastName ) + ": ";
                    }
                   text += loc( "automatic.foreman-assignment."
                                + (_.size( assi.targets ) > 1 ? "many" : "one"));
                    return {text: text, description: assi.description };
                  });
  });
};
