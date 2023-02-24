// Review request form.
// Parameters:
//   taskId: Review task id
LUPAPISTE.ReviewRequestModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.taskService;

  self.buildings = ko.observableArray();
  self.message = ko.observable();
  self.waiting = ko.observable();
  var user = lupapisteApp.models.currentUser;
  self.clientName = ko.observable( util.nonBlankJoin( [user.firstName(),
                                                       user.lastName()],
                                                      " "));
  self.clientEmail = ko.observable( user.email() );
  self.clientPhone = ko.observable( user.phone() );

  self.phoneWarning = self.disposedComputed( function() {
    var phone = _.trim( self.clientPhone() );
    if( phone && !util.isValidPhoneNumber( phone )) {
      return "notice-form.warning.phone";
    }
  });

  self.emailWarning = self.disposedComputed( function() {
    var email =  _.trim( self.clientEmail() );
    if( email &&  !util.isValidEmailAddress( email)) {
      return "notice-form.warning.email";
    }
  });

  self.canSend = self.disposedPureComputed( function() {
    return !self.waiting()
      && !_.isBlank( self.message() )
      && !_.isBlank( self.clientName() )
      && !_.isBlank( self.clientPhone() )
      && !_.isBlank( self.clientEmail() )
      && !self.emailWarning() && !self.phoneWarning();
  });

  self.request = function() {
    service.requestReview( params.taskId,
                         {"operation-ids": _( self.buildings())
                          .filter( "selected" )
                          .map( "opId")
                          .value(),
                          contact: {name: self.clientName(),
                                    email: self.clientEmail(),
                                    phone: self.clientPhone()},
                          message: self.message()},
                       self.waiting);
  };

  self.cancel = _.wrap( null, service.requestForm );

  // Initialization. Refresh buildings just in case.
  service.fetchBuildings( function( buildings ) {
    self.buildings( _.map( buildings,
                           function( b ) {
                             return _.set( b, "selected", ko.observable() );
                           }
                         ));
  });

};
