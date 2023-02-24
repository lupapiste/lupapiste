// Component that is used for filling out missing information for the
// recipient of verdict delivered via Suomi.fi-messages. Called from
// lupapalvelu.ui.pate.verdict.cljs.
//
// Note:

// Suomi.fi uses hetu or the Finnish social security number for
// sending messages to private persons.  However, querying for and
// displaying people's social security numbers is not acceptable.
//
// Therefore when the recipient dropdown is used AND the SSN of the
// selected user is known (by the backend) the name and SSN fields are
// not editable.
LUPAPISTE.RecipientDataDialogModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var data = _.get( params, "data", {});
  self.firstName = ko.observable(data["first-name"]);
  self.lastName = ko.observable(data["last-name"]);
  self.address = ko.observable(data.address);
  self.zipcode = ko.observable(data.zipcode);
  self.city = ko.observable(data.city);
  self.personId = ko.observable(data["person-id"]);

  self.personOptions = ko.observableArray();
  self.fixedPersonId = ko.observable( false );
  self.messagesAllowed = ko.observable(false);

  var applicationId = lupapisteApp.services.contextService.applicationId();
  var processing = ko.observable();
  var pending = ko.observable();

  var selected = null;

  self.selectPerson = self.disposedComputed( {
    read: function () {
      return selected;
    },
    write:  function( person ) {
      var p = person || {};
      self.firstName( p.firstName );
      self.lastName( p.lastName );
      self.personId( null );
      self.fixedPersonId( p.hasPersonId );
      self.address( p.street );
      self.zipcode( p.zip );
      self.city( p.city );
      selected = person;
    }

  });

  self.error = ko.observable();
  self.info = ko.observable();

  function getPersonId() {
    return _.toUpper( _.trim( self.personId() ));
  }

  function badPersonId() {
    return !self.fixedPersonId() && !util.isValidPersonId( getPersonId() );
  }

  var resizeDialog = _.wrap( "resize-dialog", hub.send );

  self.personIdWarning = self.disposedComputed(function() {
    if( badPersonId() && !_.isBlank( self.personId() )) {
      _.delay( resizeDialog, 100 );
      return "form.warn";
    }
  });

  self.sendDisabled = self.disposedComputed(function() {
    return _.isBlank(self.firstName())
      || _.isBlank(self.lastName())
      || _.isBlank(self.zipcode())
      || _.isBlank(self.city())
      || badPersonId();
  });

  self.sendVisible = self.disposedComputed(function() {
    return self.messagesAllowed();
  });

  self.checkAllowedVisible = self.disposedComputed(function() {
    return !self.messagesAllowed();
  });

  function errorHandler( res ) {
    self.error( res.text );
    resizeDialog();
  }

  self.optionsText = function( person ) {
    return util.nonBlankJoin( [util.nonBlankJoin( [person.lastName,
                                                   person.firstName], " "),
                               person.companyName], ", ");
  };

  function idToSend() {
    return self.fixedPersonId() ? selected.id : getPersonId();
  }

  self.sendMessage = function() {
    ajax.command("send-suomifi-verdict", {
      id: applicationId,
      verdictId: params.data["verdict-id"],
      firstName: self.firstName(),
      lastName: self.lastName(),
      personId: idToSend(),
      address: self.address(),
      zipCode: self.zipcode(),
      country: "FI",
      city: self.city()
    })
      .processing( processing )
      .pending( pending )
      .success( function() {
        hub.send( "close-dialog" );
        repository.load( applicationId );
        _.delay(params.onVerdictSent, 100);
      })
      .error(errorHandler)
      .call();
  };

  self.checkIfRecipientAllowsSuomifi = function() {
    ajax.query("fetch-recipient-suomifi-data", {
      id: applicationId,
      personId: idToSend(),
    })
      .processing( processing )
      .pending( pending )
      .success( function(response) {
        var messagesAllowedForUser = response["allows-suomifi-messages"];
        self.messagesAllowed(messagesAllowedForUser);
        if (!messagesAllowedForUser) {
          self.error("error.suomifi-messages.person-cannot-receive-messages");
        }
      })
      .error(errorHandler)
      .call();
  };

  // Populate person dropdown
  ajax.query( "user-contact-infos", {id: applicationId})
    .success( function( res ) {
      self.personOptions( _.sortBy( res.users, self.optionsText ));
    })
    .call();
};
