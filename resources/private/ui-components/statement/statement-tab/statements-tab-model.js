LUPAPISTE.StatementsTabModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.authorization = params.authModel;

  self.data = ko.observableArray([]);
  self.manualData = ko.observableArray([]);
  self.selectedPersons = ko.observableArray([]);
  self.submitting = ko.observable();
  self.showInviteSection = ko.observable();
  self.saateText = ko.observable();
  self.maaraaika = ko.observable();

  self.disabled = ko.pureComputed(function() {
    return _.isEmpty(self.selectedPersons()) || self.submitting() || _.isEmpty(self.saateText()) || !self.maaraaika();
  });

  self.addStatementGiversDisabled = ko.pureComputed(function() {
    var someManualDataDisabled = _.some(self.manualData(), function(d) {
      console.log("*** manual data disabled: ", d.isDisabled());
      return d.isDisabled(); });
    return someManualDataDisabled;
  });

  // *** TODO: Testaa automaattista rivin lisaysta, kun saat manual dataan input-kentat! ***
  self.manualData.subscribe(function(data) {
    var someManualDataDisabled = _.some(self.manualData(), function(d) { return d.isDisabled(); });
    if (!someManualDataDisabled) {
      addManualData();
    }
  });

  self.combinedData = ko.computed(function() {
    var a = self.data().concat(self.manualData());
    console.log("combinedData, self.data(): ", self.data());
    console.log("combinedData, self.manualData(): ", self.manualData());
    console.log("new combinedData: ", a);
    return a;
  });

  var dataTemplate = function() {
    var self = this;
    self.name = ko.observable("");
    self.email = ko.observable("");
    self.text = ko.observable("");
    self.readonly = ko.pureComputed(function() {
      return self.id ? true : false;
    });
    self.isDisabled = ko.pureComputed(function() {
      return _.isEmpty(self.name()) || _.isEmpty(self.email()) || _.isEmpty(self.text()) || !util.isValidEmailAddress(self.email());
    });
    return self;
  };

  self.addManualData = function() {
    self.manualData.push(new dataTemplate());
    console.log("addManualData, set self.manualData to: ", self.manualData());
  };

  self.toggleInviteSection = function() {
    self.submitting(false);
    self.saateText("");
    self.maaraaika(undefined);
    self.manualData([new dataTemplate()]);
    console.log("toggleInviteSection, set self.manualData to: ", self.manualData());
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
        dueDate: new Date(self.maaraaika()).getTime()
        };
    ajax.command("request-for-statement", params)
      .success(function() {
        self.selectedPersons([]);
        self.saateText("");
        self.maaraaika(undefined);
        self.showInviteSection(false);
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
