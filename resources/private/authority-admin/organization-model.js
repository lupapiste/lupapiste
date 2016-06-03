LUPAPISTE.OrganizationModel = function () {
  "use strict";

  var self = this;
  var authorizationModel = lupapisteApp.models.globalAuthModel;

  self.initialized = false;

  function EditLinkModel() {
    var self = this;

    self.nameFi = ko.observable();
    self.nameSv = ko.observable();
    self.url = ko.observable();
    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.nameFi(util.getIn(params, ["source", "name", "fi"], ""));
      self.nameSv(util.getIn(params, ["source", "name", "sv"], ""));
      self.url(util.getIn(params, ["source", "url"], ""));
    };

    self.execute = function() {
      self.command(self.url(), self.nameFi(), self.nameSv());
    };

    self.ok = ko.computed(function() {
      return !_.isBlank(self.nameFi()) && !_.isBlank(self.nameSv()) && !_.isBlank(self.url());
    });
  }
  self.editLinkModel = new EditLinkModel();

  self.organizationId = ko.observable();
  self.links = ko.observableArray();
  self.operationsAttachments = ko.observableArray();
  self.attachmentTypes = {};
  self.selectedOperations = ko.observableArray();
  self.allOperations = [];
  self.appRequiredFieldsFillingObligatory = ko.observable(false);
  self.validateVerdictGivenDate = ko.observable(true);
  self.tosFunctions = ko.observableArray();
  self.tosFunctionVisible = ko.observable(false);
  self.permanentArchiveEnabled = ko.observable(true);
  self.permanentArchiveInUseSince = ko.observable();
  self.features = ko.observable();
  self.allowedRoles = ko.observable([]);

  self.permitTypes = ko.observable([]);

  self.load = function() { ajax.query("organization-by-user").success(self.init).call(); };

  ko.computed(function() {
    var isObligatory = self.appRequiredFieldsFillingObligatory();
    if (self.initialized) {
      ajax.command("set-organization-app-required-fields-filling-obligatory", {enabled: isObligatory})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  ko.computed(function() {
    var validateVerdictGivenDate = self.validateVerdictGivenDate();
    if (self.initialized) {
      ajax.command("set-organization-validate-verdict-given-date", {enabled: validateVerdictGivenDate})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  self.validateVerdictGivenDateVisible = ko.pureComputed(function() {
    var types = self.permitTypes();
    return _.includes(types, "R") || _.includes(types, "P");
  });

  function toAttachments(attachments) {
    return _(attachments || [])
      .map(function(a) { return {id: a, text: loc(["attachmentType", a[0], a[1]])}; })
      .sortBy("text")
      .value();
  }

  self.neighborOrderEmails = ko.observable("");
  self.neighborOrderEmailsIndicator = ko.observable().extend({notify: "always"});
  ko.computed(function() {
    var emails = self.neighborOrderEmails();
    if (self.initialized) {
      ajax.command("set-organization-neighbor-order-email", {emails: emails})
        .success(_.partial(self.neighborOrderEmailsIndicator, {type: "saved"}))
        .error(_.partial(self.neighborOrderEmailsIndicator, {type: "err"}))
        .call();
    }
  });

  self.submitNotificationEmails = ko.observable("");
  self.submitNotificationEmailsIndicator = ko.observable().extend({notify: "always"});
  ko.computed(function() {
    var emails = self.submitNotificationEmails();
    if (self.initialized) {
      ajax.command("set-organization-submit-notification-email", {emails: emails})
        .success(_.partial(self.submitNotificationEmailsIndicator, {type: "saved"}))
        .error(_.partial(self.submitNotificationEmailsIndicator, {type: "err"}))
        .call();
    }
  });

  self.infoRequestNotificationEmails = ko.observable("");
  self.infoRequestNotificationEmailsIndicator = ko.observable().extend({notify: "always"});
  ko.computed(function() {
    var emails = self.infoRequestNotificationEmails();
    if (self.initialized) {
      ajax.command("set-organization-inforequest-notification-email", {emails: emails})
        .success(_.partial(self.infoRequestNotificationEmailsIndicator, {type: "saved"}))
        .error(_.partial(self.infoRequestNotificationEmailsIndicator, {type: "err"}))
        .call();
    }
  });

  self.init = function(data) {
    var organization = data.organization;
    self.organizationId(organization.id);
    ajax
      .query("all-operations-for-organization", {organizationId: organization.id})
      .success(function(data) {
        self.allOperations = data.operations;
      })
      .call();

    // Required fields in app obligatory to submit app
    //
    self.appRequiredFieldsFillingObligatory(organization["app-required-fields-filling-obligatory"] || false);

    self.validateVerdictGivenDate(organization["validate-verdict-given-date"] === true);

    self.permanentArchiveEnabled(organization["permanent-archive-enabled"] || false);
    self.permanentArchiveInUseSince(new Date(organization["permanent-archive-in-use-since"] || 0));
    ko.computed(function() {
      var startDate = self.permanentArchiveInUseSince();
      if (self.initialized && startDate) {
        ajax.command("set-organization-permanent-archive-start-date", {date: startDate.getTime()})
          .success(util.showSavedIndicator)
          .error(util.showSavedIndicator)
          .call();
      }
    });

    // Operation attachments
    //
    var operationsAttachmentsPerPermitType = organization.operationsAttachments || {};
    var localizedOperationsAttachmentsPerPermitType = [];
    self.links(organization.links || []);

    var operationsTosFunctions = organization["operations-tos-functions"] || {};

    var setTosFunctionForOperation = function(operationId, functionCode) {
      var cmd = functionCode !== null ? "set-tos-function-for-operation" : "remove-tos-function-from-operation";
      var data = {operation: operationId};
      if (functionCode !== null) {
        data.functionCode = functionCode;
      }
      ajax
        .command(cmd, data)
        .success(self.load)
        .call();
    };

    self.neighborOrderEmails(util.getIn(organization, ["notifications", "neighbor-order-emails"], []).join("; "));
    self.submitNotificationEmails(util.getIn(organization, ["notifications", "submit-notification-emails"], []).join("; "));
    self.infoRequestNotificationEmails(util.getIn(organization, ["notifications", "inforequest-notification-emails"], []).join("; "));

    _.forOwn(operationsAttachmentsPerPermitType, function(value, permitType) {
      var operationsAttachments = _(value)
        .map(function(v, k) {
          var attrs = {
            id: k,
            text: loc(["operations", k]),
            attachments: toAttachments(v),
            permitType: permitType,
            tosFunction: ko.observable(operationsTosFunctions[k])
          };
          attrs.tosFunction.subscribe(function(newFunctionCode) {
            setTosFunctionForOperation(k, newFunctionCode);
          });
          return attrs;
        })
        .sortBy("text")
        .value();
      localizedOperationsAttachmentsPerPermitType.push({permitType: permitType, operations: operationsAttachments});
    });

    self.operationsAttachments(localizedOperationsAttachmentsPerPermitType);
    self.attachmentTypes = data.attachmentTypes;

    // Selected operations
    //
    var selectedOperations = organization.selectedOperations || {};
    var localizedSelectedOperationsPerPermitType = [];

    _.forOwn(selectedOperations, function(value, permitType) {
      var selectedOperations = _(value)
        .map(function(v) {
          return {
            id: v,
            text: loc(["operations", v]),
            permitType: permitType
            };
          })
        .sortBy("text")
        .value();
      localizedSelectedOperationsPerPermitType.push({permitType: permitType, operations: selectedOperations});
    });

    self.selectedOperations(_.sortBy(localizedSelectedOperationsPerPermitType, "permitType"));

    // TODO test properly for timing issues
    if (authorizationModel.ok("available-tos-functions")) {
      ajax
        .query("available-tos-functions", {organizationId: organization.id})
        .success(function(data) {
          self.tosFunctions([{code: null, name: ""}].concat(data.functions));
          if (data.functions.length > 0 && organization["permanent-archive-enabled"]) {
            self.tosFunctionVisible(true);
          }
        })
        .call();
    }

    self.features(util.getIn(organization, ["areas"]));

    self.allowedRoles(organization.allowedRoles);

    self.permitTypes(_(organization.scope).map("permitType").uniq().value());

    self.initialized = true;
  };

  self.editLink = function(indexFn) {
    var index = indexFn();
    self.editLinkModel.init({
      source: this,
      commandName: "edit",
      command: function(url, nameFi, nameSv) {
        ajax
          .command("update-organization-link", {index: index, url: url, nameFi: nameFi, nameSv: nameSv})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call();
      }
    });
    self.openLinkDialog();
  };

  self.addLink = function() {
    self.editLinkModel.init({
      commandName: "add",
      command: function(url, nameFi, nameSv) {
        ajax
          .command("add-organization-link", {url: url, nameFi: nameFi, nameSv: nameSv})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call();
      }
    });
    self.openLinkDialog();
  };

  self.rmLink = function() {
    ajax
      .command("remove-organization-link", {url: this.url, nameFi: this.name.fi, nameSv: this.name.sv})
      .success(self.load)
      .call();
  };

  self.openLinkDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-link");
  };
};
