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

  function DialogData() {
    var dd = this;
    dd.email     = ko.observable();
    dd.firstName = ko.observable();
    dd.lastName  = ko.observable();
    dd.role      = ko.observable();
    dd.waitingEmail = ko.observable();
    dd.waitingOk = ko.observable();
    dd.error     = ko.observable();
    dd.oldUser   = ko.observable();
    dd.reset = function() {
      dd.email( "" );
      dd.firstName( "" );
      dd.lastName( "" );
      dd.role( "" );
      dd.waitingEmail( false );
      dd.waitingOk( false );
      dd.error( null );
      dd.oldUser( false );
    };
    dd.isGood = ko.pureComputed( function() {
      return !dd.error()
          && !(dd.waitingEmail() || dd.waitingOk())
          && util.isValidEmailAddress( dd.email())
        && _.every( ["firstName", "lastName"],
                    function( k ) {
                      var s = dd[k]();
                      return s && s.trim() !== "";
                    });
    });
    dd.namesEditable = ko.pureComputed( function() {
      return !(dd.waitingEmail()
             || dd.oldUser());
    });
    dd.getUser = ko.computed( function() {
      if( util.isValidEmailAddress( dd.email())) {
        ajax.query ( "resolve-guest-authority-candidate",
                     {email: dd.email()})
        .pending ( dd.waitingEmail )
        .success ( function( res ) {
          dd.error( false );
          dd.firstName( res.user.firstName );
          dd.lastName( res.user.lastName );
          dd.oldUser( res.user.firstName || res.user.lastName );
        })
        .error ( function() {
          dd.error( true);
          dd.oldUser( false );
        })
        .call();
      } else {
        dd.error( true );
        dd.oldUser( false );
      }
    });
  }

  self.dialogData = new DialogData();

  self.dialog = function() {
    if (!self._dialog) {
      self._dialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
      self._dialog.createMask();
    }
    return self._dialog;
  };

  self.addGuest = function() {
    self.dialogData.reset();
    self.dialog().open( "#dialog-add-guest-authority");
  };

  self.guestClick = function() {
    // We always add the user to organization just in case.
    // Note: for an already existing organization authority,
    // the role is diminished to guest
    ajax.command( "update-user-organization", {email: self.dialogData.email(),
                                              firstName: self.dialogData.firstName(),
                                              lastName: self.dialogData.lastName(),
                                              roles: ["guest"]})
    .pending( self.dialogData.waitingOk )
    .success( function( res ) {
      console.log( "success", res);
    })
    .error( function (res ) {
      console.log( "error", res );

    })
    .call();
  };
};
