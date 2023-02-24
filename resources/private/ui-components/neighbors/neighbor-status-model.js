// Contents for the neighbor status dialog.
// Params:
//   status: Unwrapped (no observables) current (last) status object
//   of a neighbor.
LUPAPISTE.NeighborStatusModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var status = params.status;
  var u = status.vetuma || status.user;
  self.state =   status.state;
  self.created = status.created;
  self.message = status.message;
  self.firstName = u.firstName;
  self.lastName =  u.eidasId ? u.eidasFamilyName : u.lastName;
  self.userid =  u.eidasId ||  u.userid ;
};
