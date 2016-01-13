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
    dd.waiting   = ko.observable();
    dd.error     = ko.observable();
    dd.oldUser   = ko.observable();
    dd.reset = function() {
      dd.email( "" );
      dd.firstName( "" );
      dd.lastName( "" );
      dd.role( "" );
      dd.waiting( false );
      dd.error( null );
      dd.oldUser( false );
    };
    dd.isGood = ko.pureComputed( function() {
      return !dd.error()
          && !dd.waiting()
          && util.isValidEmailAddress( dd.email())
        && _.every( ["firstName", "lastName"],
                    function( k ) {
                      var s = dd[k]();
                      return s && s.trim() !== "";
                    });
    });
    dd.namesEditable = ko.pureComputed( function() {
      return !(dd.waiting()
             || dd.oldUser());
    });
    dd.getUser = ko.computed( function() {
      var addr =
      ajax.query( "user-by-email", {email: dd.email()})
      .pending( dd.waiting )
      .success( function( res ) {
        dd.error( false );
        dd.oldUser( true );
        console.log( "Response:", res );
      })
      .error( function() {
        dd.error( true);
        dd.oldUser( false );
      });
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
    _.each( self.dialogData, function( v, k ) {
      console.log( k, ":", v());
    });
  };
};
