LUPAPISTE.StatementsTabModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.application = params.application;
  self.authorization = params.authModel;

  self.data = ko.observableArray([]);
  self.manualData = ko.observableArray([]);
  self.submitting = ko.observable();
  self.showInviteSection = ko.observable();
  self.saateText = ko.observable();
  self.maaraaika = ko.observable();
  self.dateInvalid = ko.observable();

  // ELY statement specifics
  var ELY_MESSAGE_SIZE_LIMIT = 1000;
  self.showElyStatementSection = ko.observable();
  self.elySubtypes = ko.observableArray(); // from query, subtypes differ for each permit type
  self.elyData = {subtype: ko.observable(),
                  saateText: ko.observable(),
                  lang: ko.observable(),
                  dueDate: ko.observable()};

  self.resetElyData = function() {
    self.elyData.subtype(null);
    self.elyData.saateText("");
    self.elyData.dueDate(null);
    self.elyData.lang( loc.getCurrentLanguage() );
  };

  var elyDateInvalid = ko.observable();
  self.elyDataOk = ko.pureComputed(function() {
    return self.elyData.subtype() && !elyDateInvalid();
  });
  self.elyTextLength = self.disposedPureComputed(function () {
    return _.size( _.trim( self.elyData.saateText() ));
  });
  self.elyTextOverLimit = self.disposedPureComputed( function() {
    return self.elyTextLength() > ELY_MESSAGE_SIZE_LIMIT;
  });

  self.isAfterToday = function( m ) {
    var today = moment().startOf( "day" );
    return m.isAfter( today );
  };

  function dueDateCallback( valueObs, invalidObs, obj ) {
    invalidObs( !obj.isValid );
    valueObs( obj.moment && obj.moment.valueOf() );
  }

  self.regularDueDateCallback = _.partial( dueDateCallback, self.maaraaika, self.dateInvalid );
  self.elyDueDateCallback = _.partial( dueDateCallback, self.elyData.dueDate, elyDateInvalid );

  self.requestElyStatement = function() {
    ajax.command("ely-statement-request", _.merge(ko.mapping.toJS(self.elyData),
                                                  {id: self.application.id(),
                                                   dueDate: self.elyData.dueDate() ? new Date(self.elyData.dueDate()).getTime() : null}))
      .pending(self.submitting)
      .success(function(resp) {
        util.showSavedIndicator(resp);
        repository.load(self.application.id(), self.submitting, null, true);
        self.showElyStatementSection(false);
        self.resetElyData();
      })
      .onError("error.integration.create-message", function(e) {
        hub.send("show-dialog", {ltitle: "integration.asianhallinta.title",
                                 size: "large",
                                 component: "integration-error-dialog",
                                 componentParams: {ltext: "error.ely-statement.xml-error",
                                                   details: e.details}});
      })
      .call();
  };

  self.someDialogOpen = ko.pureComputed(function() {
    return self.showInviteSection() || self.showElyStatementSection();
  });

  self.combinedData = self.disposedPureComputed(function() {
    return self.data().concat(self.manualData());
  });

  function isSelected( m ) {
    return m.selected();
  }

  self.selectedPersons = self.disposedComputed( function() {
    if( _.every( self.manualData(), isSelected )) {
      self.manualData.push( new DataTemplate() );
    }
    return _.filter( self.combinedData(), isSelected );
  });

  self.canSend = self.disposedPureComputed(function() {
    var selected = self.selectedPersons();
    return !self.dateInvalid()
      && !_.isEmpty( selected )
      && _.every( selected, function( person ) {
        return person.isOk();
    });
  });

  function DataTemplate() {
    var selfie = this;
    ko.utils.extend(selfie, new LUPAPISTE.ComponentBaseModel());
    selfie.selected = ko.observable( false );
    selfie.text = ko.observable("");
    selfie.name = ko.observable("");
    selfie.email = ko.observable("");
    // Make the statement givers entered by authority admin
    // (self.data) read-only. Those have an ID, others (dynamically
    // input ones - self.manualData) do not.
    selfie.readonly = selfie.disposedPureComputed(function() {
      return selfie.id ? true : false;
    });
    selfie.required = selfie.disposedPureComputed( function() {
      return {text: _.isBlank( selfie.text()),
              name: _.isBlank( selfie.name()),
              email: _.isBlank( selfie.email())};
    });
    selfie.emailError = selfie.disposedPureComputed( function() {
      var email = _.trim( selfie.email() );
      return !(_.isBlank( email ) || util.isValidEmailAddress( email));
    });
    selfie.isOk = selfie.disposedPureComputed( function() {
      return _.every( selfie.required(), _.partial( _.isEqual, false ))
        && !selfie.emailError();
    });
    return selfie;
  }

  function clearPersonSelections() {
    _.forEach( self.selectedPersons(),
               function( person ) {
                 person.selected( false );
               });
  }

  self.toggleInviteSection = function() {
    self.submitting(false);
    self.saateText("");
    self.manualData([new DataTemplate()]);
    clearPersonSelections();
    self.maaraaika(undefined);
    self.showInviteSection( !self.showInviteSection() );
  };

  self.send = function() {
    var selPersons = _(ko.mapping.toJS(self.selectedPersons()))
        .map(function(p) { return _.pick(p, ["id", "email", "name", "text"]); })
        .value();
    var params = {
        functionCode: (self.application.tosFunction() || null),
        id: self.application.id(),
        selectedPersons: selPersons,
        saateText: self.saateText(),
        dueDate: self.maaraaika()
        };
    ajax.command("request-for-statement", params)
      .success(function() {
        hub.send("indicator", {style: "positive", message: "application-statement-giver-invite.success"});
        self.maaraaika( undefined );
        clearPersonSelections();
        repository.load(self.application.id());
      })
      .pending(self.submitting)
      .call();
  };

  self.openNeighborsPage = function(model) {
    pageutil.openPage("neighbors", model.application.id());
    return false;
  };


  (function() {
    if (self.authorization.ok("get-statement-givers")) {
      ajax
      .query("get-statement-givers", {id: self.application.id()})
      .success(function(result) {
        var fetchedStatementGivers = _.map(result.data, function(arrObj) {
          return ko.mapping.fromJS(arrObj, {}, new DataTemplate());
        });
        self.data(fetchedStatementGivers);
      })
      .call();
    }
    if (self.authorization.ok("ely-statement-request")) {
      ajax.query("ely-statement-types", {id: self.application.id()})
        .success(function(result) {
          self.elySubtypes(result.statementTypes);
        })
        .call();
    }
  })();

};
