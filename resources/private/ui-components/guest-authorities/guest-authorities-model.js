LUPAPISTE.GuestAuthoritiesModel = function() {
  "use strict";
  var self = this;
  self.guests = ko.observableArray([{role: "Rooli", name: "Etu Suku", email: "foo@bar.com"}]);
  self.readOnly = ko.pureComputed( function() {
    return false;
  });
  self.removeGuest = function( data ) {
    console.log( "Remove", data);
  };

  // Dialog
  self.email = ko.observable();
  self.firstName = ko.observable();
  self.lastName = ko.observable();
  self.role = ko.observable();

  self.dialog = function() {
    if (!self._dialog) {
      self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
      self._dialog.createMask();
    }
    return self._dialog;
  };

  self.addGuest = function() {
    self.dialog().open( "#dialog-add-guest-authority");
  };

  self.guestClick = function() {
    console.log( self.email(), self.firstName(), self.lastName(), self.role());
  };

  self.guestGood = function() {

  };
};
