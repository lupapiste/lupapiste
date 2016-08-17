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
  self.useAttachmentLinksIntegration = ko.observable(false);

  self.sectionOperations = ko.observableArray();

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

  ko.computed(function() {
    var useAttachmentLinks = self.useAttachmentLinksIntegration();
    if (self.initialized) {
      ajax.command("set-organization-use-attachment-links-integration", {enabled: useAttachmentLinks})
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
  ko.computed(function() {
    var emails = self.neighborOrderEmails();
    if (self.initialized) {
      ajax.command("set-organization-neighbor-order-email", {emails: emails})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  self.submitNotificationEmails = ko.observable("");
  ko.computed(function() {
    var emails = self.submitNotificationEmails();
    if (self.initialized) {
      ajax.command("set-organization-submit-notification-email", {emails: emails})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  self.infoRequestNotificationEmails = ko.observable("");
  ko.computed(function() {
    var emails = self.infoRequestNotificationEmails();
    if (self.initialized) {
      ajax.command("set-organization-inforequest-notification-email", {emails: emails})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  ko.computed(function() {
    var startDate = self.permanentArchiveInUseSince();
    if (self.initialized && startDate) {
      ajax.command("set-organization-permanent-archive-start-date", {date: startDate.getTime()})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }
  });

  var sectionEnabled = ko.observable();

  self.verdictSectionEnabled = ko.computed( {
      read: function() {
        return sectionEnabled();
      },
      write: function( enabled ) {
        sectionEnabled( Boolean( enabled ));
        if( self.initialized ) {
          ajax.command( "section-toggle-enabled",
                        {flag: sectionEnabled()})
            .success( util.showSavedIndicator)
            .error( util.showSavedIndicator)
            .call();
        }
      }
    });


  self.init = function(data) {
    self.initialized = false;
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

    self.useAttachmentLinksIntegration(organization["use-attachment-links-integration"] === true);

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

    // Section requirement for verdicts.
    sectionEnabled( _.get( organization, "section.enabled"));

    self.sectionOperations(_.get( organization, "section.operations", []));

    self.initialized = true;
  };



  self.isSectionOperation = function ( $data )  {
    return self.sectionOperations.indexOf( $data.id ) >= 0;
  };

  self.toggleSectionOperation = function( $data ) {
    var flag = !self.isSectionOperation( $data );
    if( flag ) {
      self.sectionOperations.push( $data.id );
    } else {
      self.sectionOperations.remove( $data.id );
    }
    ajax.command( "section-toggle-operation", {operationId: $data.id,
                                               flag: flag })
      .call();
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
