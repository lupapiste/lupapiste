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
    return _.isEmpty(self.selectedPersons()) || self.submitting();
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
      if (errs.length == 0 && self.showInviteSection && self.showInviteSection()) {
        self.selectedPersons.push(selfie);
        addManualData();
      } else {
        self.selectedPersons.remove(selfie);
      }
    });
    return selfie;
  };

  self.toggleInviteSection = function() {
    self.submitting(false);
    self.saateText("");
    self.selectedPersons([]);
    self.maaraaika(undefined);
    self.manualData([new dataTemplate()]);
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
        dueDate: self.maaraaika() ? new Date(self.maaraaika()).getTime() : null
        };
    ajax.command("request-for-statement", params)
      .success(function() {
        hub.send("indicator", {style: "positive", message: "application-statement-giver-invite.success"});
        self.selectedPersons([]);
        self.maaraaika(undefined);
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
          return ko.mapping.fromJS(arrObj, {}, new dataTemplate());
        });
        self.data(fetchedStatementGivers);
      })
      .call();
    }
  })();

};
