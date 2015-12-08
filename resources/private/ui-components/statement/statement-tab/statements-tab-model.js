LUPAPISTE.StatementsTabModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.authorization = params.authModel;

  self.data = ko.observableArray([]);
  self.manualData = ko.observableArray([]);
  self.selectedPerson = ko.observable();
  self.submitting = ko.observable();
  self.showInviteSection = ko.observable();
  self.saateText = ko.observable();
  self.maaraaika = ko.observable();

  self.disabled = ko.pureComputed(function() {
    return !self.selectedPerson() || self.submitting() || _.isEmpty(self.saateText()) || !self.maaraaika();
  });

  self.combinedData = ko.computed(function() {
    return self.data().concat(self.manualData());
  });

  var addManualData = function() {
    var allManualDatasEnabled = _.every(self.manualData(), function(d) { return d.errors().length == 0; });
    if (allManualDatasEnabled) {
      self.manualData.push(new dataTemplate());
      return self;
    }
  };

  var dataTemplate = function() {
    var selfie = this;
    selfie.text = ko.observable("").extend({ required: true });
    selfie.name = ko.observable("").extend({ required: true });
    selfie.email = ko.observable("").extend({ required: true, email: true });
    // Make the statement givers entered by authority admin (self.data) read-only. Those have an ID, others (dynamically input ones - self.manualData) do not.
    selfie.readonly = ko.pureComputed(function() {
      return selfie.id ? true : false;
    });
    selfie.errors = ko.validation.group(selfie);
    selfie.errors.subscribe(function(errs) {
//      if (errs.length == 0) addManualData();  // TODO: This is temporarily commented out, until the specs for multiple addition of statement givers will be defined
      if (errs.length == 0 && self.showInviteSection && self.showInviteSection()) {
        self.selectedPerson(selfie);
      } else {
        self.selectedPerson(undefined);
      }
    });
    return selfie;
  };

  self.toggleInviteSection = function() {
    self.submitting(false);
    self.saateText("");
    self.selectedPerson(undefined);
    self.maaraaika(undefined);
    self.manualData([new dataTemplate()]);
    self.showInviteSection( !self.showInviteSection() );
  };

  self.send = function() {
    var selPerson = _(ko.mapping.toJS(self.selectedPerson()))
                      .pick(["id", "email", "name", "text"])
                      .value();
    var params = {
        functionCode: (self.application.tosFunction() || null),
        id: self.application.id(),
        selectedPersons: [selPerson],
        saateText: self.saateText(),
        dueDate: new Date(self.maaraaika()).getTime()
        };
    ajax.command("request-for-statement", params)
      .success(function() {
        hub.send("indicator", {style: "positive", message: "application-statement-giver-invite.success"});
        self.selectedPerson(undefined);
        self.maaraaika(undefined);
        repository.load(self.application.id());
      })
      .pending(self.submitting)
      .call();
  };

  (function() {
    if (self.authorization.ok("get-statement-givers")) {
      ajax
      .query("get-statement-givers", {id: self.application.id()})
      .success(function(result) {
        var fetchedStatementGivers = _.map(result.data, function(arrObj) {
          return ko.mapping.fromJS(arrObj, {}, new dataTemplate());
        });
        self.data(fetchedStatementGivers);
      })
      .call();
    }
  })();

  self.openStatement = function(model) {
    pageutil.openPage("statement", self.application.id() + "/" + model.id());
    return false;
  };

  // TODO: Kopio applications.js:sta. -> Tee neighborsista uusi oma komponentti, ja siirra tama sinne.
  self.neighborActions = {
    manage: function(model) {
      pageutil.openPage("neighbors", model.application.id());
      return false;
    },
    markDone: function(neighbor) {
      ajax
        .command("neighbor-mark-done", {id: currentId, neighborId: neighbor.id(), lang: loc.getCurrentLanguage()})
        .complete(_.partial(repository.load, currentId, _.noop))
        .call();
    },
    statusCompleted: function(neighbor) {
      return _.contains(["mark-done", "response-given-ok", "response-given-comments"], _.last(neighbor.status()).state());
    },
    showStatus: function(neighbor) {
      neighborStatusModel.init(neighbor).open();
      return false;
    }
  };

};
