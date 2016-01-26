LUPAPISTE.GuestAuthoritiesModel = function() {
  "use strict";
  var self = this;
  self.guests = ko.observableArray();

self.canEdit = function() {
    return lupapisteApp.models.globalAuthModel.ok( "update-user-organization" );
  };


  function fetchGuestAuthorities() {
    ajax.query( "guest-authorities-organization")
    .success( function( res ) {
      self.guests( res.guestAuthorities );
    })
    .call ();
  }

  // Initialization
  fetchGuestAuthorities();

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
    dd.errorMessage = ko.observable();
    dd.reset = function() {
      dd.email( "" );
      dd.firstName( "" );
      dd.lastName( "" );
      dd.role( "" );
      dd.waitingEmail( false );
      dd.waitingOk( false );
      dd.error( null );
      dd.oldUser( false );
      dd.errorMessage("");
    };
    dd.isGood = ko.pureComputed( function() {
      return !dd.error()
          && !dd.errorMessage()
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
          dd.errorMessage( res.user.hasAccess ? "guest-authority.has-access" : "");
        })
        .error ( function() {
          dd.error( false );
          dd.oldUser( false );
          dd.errorMessage( "guest-authority.failure");
        })
        .call();
      } else {
        dd.error( true );
        dd.oldUser( false );
      }
    });
    dd.guestClick = function() {
      // We always add the user to organization just in case.
      // Note: for an already existing organization authority,
      // the role is diminished to guestAuthority
      ajax.command( "update-user-organization", {email: dd.email(),
                                                 firstName: dd.firstName(),
                                                 lastName: dd.lastName(),
                                                 roles: ["guestAuthority"]})
      .pending( dd.waitingOk )
      .success( function() {
        ajax.command( "update-guest-authority-organization",
                      {email: dd.email(),
                       name: dd.firstName() + " " + dd.lastName(),
                       role: dd.role()})
     .pending( dd.waitingOk )
        .success( function() {
          fetchGuestAuthorities();
          // Organizations users
          hub.send( "redraw-users-list");
          LUPAPISTE.ModalDialog.close();
        })
        .call();
      })
      .error( function ( res ) {
        var err = res.text;
        if( err === "error.user-not-found") {
          err = "error.not-authority";
        }
        dd.errorMessage( err || "guest-authority.failure" );
      })
      .call();
    };
  }

  self.dialogData = new DialogData();


  // Table operations

  self.addGuest = function() {
    self.dialogData.reset();
    LUPAPISTE.ModalDialog.open( "#dialog-add-guest-authority");
  };
  self.removeGuest = function( data ) {
    ajax.command( "remove-guest-authority-organization", {email: data.email})
    .success( fetchGuestAuthorities )
    .call();
  };
};
