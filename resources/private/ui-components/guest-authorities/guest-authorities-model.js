LUPAPISTE.GuestAuthoritiesModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.guests = ko.observableArray();

  self.canEdit = function() {
    return lupapisteApp.models.globalAuthModel.ok( "upsert-organization-user" );
  };

  function fetchGuestAuthorities() {
    ajax.query("guest-authorities-organization")
        .success(function(res) { self.guests(res.guestAuthorities); })
        .call();
  }

  // Initialization
  fetchGuestAuthorities();

  // Dialog

  function DialogData() {
    var dd = this;
    dd.email     = ko.observable();
    dd.firstName = ko.observable();
    dd.lastName  = ko.observable();
    dd.description      = ko.observable();
    dd.waitingEmail = ko.observable();
    dd.waitingOk = ko.observable();
    dd.error     = ko.observable();
    dd.oldUser   = ko.observable();
    dd.errorMessage = ko.observable();
    dd.financialAuthority = ko.observable();
    dd.reset = function() {
      dd.email( "" );
      dd.firstName( "" );
      dd.lastName( "" );
      dd.description( "" );
      dd.waitingEmail( false );
      dd.waitingOk( false );
      dd.error( null );
      dd.oldUser( false );
      dd.errorMessage("");
      dd.financialAuthority(false);
    };
    function namesFilled() {
      return _.every( ["firstName", "lastName"],
                    function( k ) {
                      var s = dd[k]();
                      return s && s.trim() !== "";
                    });
    }
    dd.isGood = self.disposedPureComputed( function() {
      return !dd.error()
          && !dd.errorMessage()
          && !(dd.waitingEmail() || dd.waitingOk())
          && util.isValidEmailAddress( dd.email())
          && namesFilled();
    });
    dd.namesEditable = self.disposedPureComputed( function() {
      return !(dd.waitingEmail()
             || dd.oldUser()
             || dd.financialAuthority());
    });
    dd.getUser = self.disposedComputed( function() {
      if( util.isValidEmailAddress( dd.email())) {
        ajax.query("resolve-guest-authority-candidate", {email: dd.email()})
        .pending ( dd.waitingEmail )
        .success ( function( res ) {
          dd.error( false );
          dd.firstName( res.user.firstName );
          dd.lastName( res.user.lastName );
          // We treat the user as known only if both names are originally filled.
          // This way, admin authority can still modify users with missing names.
          dd.oldUser( namesFilled() );
          dd.errorMessage( res.user.hasAccess ? "guest-authority.has-access" : "");
          if ( dd.errorMessage() === "" ) {
            dd.errorMessage (res.user.financialAuthority ? "error.is-financial-authority" : "");
          }
          dd.financialAuthority(res.user.financialAuthority);
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
        ajax.command( "update-guest-authority-organization",
                      {email: dd.email(),
                       firstName: dd.firstName(),
                       lastName: dd.lastName(),
                       description: dd.description()})
     .pending( dd.waitingOk )
        .success( function() {
          fetchGuestAuthorities();
          // Organizations users
          hub.send( "redraw-users-list");
          LUPAPISTE.ModalDialog.close();
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
    LUPAPISTE.ModalDialog.showDynamicYesNo( loc( "guest-authority.remove-title"),
                                            loc( "guest-authority.remove-body"),
                                            {title: loc( "yes"),
                                             fn: function() {
                                               ajax.command( "remove-guest-authority-organization",
                                                             {email: data.email})
                                               .success( fetchGuestAuthorities )
                                               .call();}});
  };
};
