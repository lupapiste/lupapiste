LUPAPISTE.OrganizationModel = function () {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var authorizationModel = lupapisteApp.models.globalAuthModel;

  self.initialized = false;

  function EditLinkModel() {
    var self = this;

    self.links = ko.observableArray();
    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.links.removeAll();
      self.links(_.map(params.langs, function(lang) {
        return {lang: lang,
                name: ko.observable(util.getIn(params, ["source", "name", lang], "")),
                url:  ko.observable(util.getIn(params, ["source", "url",  lang], ""))};
      }));
    };

    self.execute = function() {
      self.command(self.links());
    };

    self.ok = ko.computed(function() {
      return _.every(self.links(), function (l) {
        return !_.isBlank(l.name()) && !_.isBlank(l.url());
      });
    });
  }
  self.editLinkModel = new EditLinkModel();

  self.organizationId = ko.observable();
  self.langs = ko.observableArray();
  self.links = ko.observableArray();
  self.operationsAttachments = ko.observableArray();
  self.attachmentTypes = {};
  self.attachmentTypesDocdepartmental = {};
  self.selectedOperations = ko.observableArray();
  self.allOperations = [];
  self.appRequiredFieldsFillingObligatory = ko.observable(false);
  self.planInfoDisabled = ko.observable(false);
  self.automaticOkForAttachments = ko.observable(false);
  self.assignmentsEnabled = ko.observable(false);
  self.ramDisabled = ko.observable(false);
  self.ramMessage = {fi: ko.observable(""),
                     sv: ko.observable(""),
                     en: ko.observable("")};
  self.suomifiMessagesVerdictEnabled = ko.observable(false);
  self.suomifiMessagesVerdictMessage = ko.observable("");
  self.suomifiMessagesNeighborsEnabled = ko.observable(false);
  self.suomifiMessagesNeighborsMessage = ko.observable("");
  self.extendedConstructionWasteReportEnabled = ko.observable(false);
  self.validateVerdictGivenDate = ko.observable(true);
  self.automaticConstructionStarted = ko.observable( true );
  self.automaticReviewFetchEnabled = ko.observable(true);
  self.onlyUseInspectionFromBackend = ko.observable(false);
  self.tosFunctions = ko.observableArray();
  self.tosFunctionVisible = ko.observable(false);
  self.archivingProjectTosFunction = ko.observable();
  self.permanentArchiveEnabled = ko.observable(true);
  self.permanentArchiveInUseSince = ko.observable();
  self.earliestArchivingDate = ko.observable();
  self.reviewOfficersListEnabled = ko.observable(false);
  self.features = ko.observable();
  self.allowedRoles = ko.observable([]);
  self.permitTypes = ko.observable([]);
  self.useAttachmentLinksIntegration = ko.observable(false);
  self.inspectionSummariesEnabled = ko.observable(false);
  self.inspectionSummaryTemplates = ko.observableArray([]);
  self.operationsInspectionSummaryTemplates = ko.observable({});
  self.handlerRoles = ko.observableArray();
  self.automaticAssignmentFilters = ko.observableArray();
  self.handlingTime = ko.observable(null);
  self.handlingTimeEnabled = ko.observable(false);
  self.multipleOperationsSupported = ko.observable(false);
  self.noCommentNeighborAttachmentEnabled = ko.observable(false);
  self.removeHandlersFromRevertedDraft = ko.observable(false);
  self.removeHandlersFromConvertedApplication = ko.observable(false);
  self.stateChangeMsgEnabled = ko.observable(false);
  self.stateChangeMsgUrl = ko.observable("");
  self.stateChangeMsgHeaders = ko.observableArray([]);
  self.stateChangeConf = ko.observable();
  self.foremanTerminationRequestEnabled = ko.observable(false);

  self.sectionOperations = ko.observableArray();
  self.noticeForms = ko.observable();
  self.reviewPdf = ko.observable();

  self.attachmentExportFiles = ko.observableArray([]);

  var defaultAttachmentsMandatory = ko.observable({});

  function save(command, params) {
    ajax.command(command, params)
      .success(util.showSavedIndicator)
      .error(util.showSavedIndicator)
      .call();
  }

  self.damForOperation = function( op ) {
    return defaultAttachmentsMandatory()[op];
  };

  self.toggleDamForOperation = function( op, isMandatory ) {
    defaultAttachmentsMandatory( _.set( defaultAttachmentsMandatory(),
                                        op,
                                        isMandatory));
    save("toggle-default-attachments-mandatory-operation",
      {organizationId: self.organizationId(), operationId: op, mandatory: isMandatory});
  };

  self.toggleSuomifiVerdict = function() {
    var newValue = !self.suomifiMessagesVerdictEnabled();
    self.suomifiMessagesVerdictEnabled(newValue);
    if (self.initialized) {
      ajax.command("toggle-organization-suomifi-messages", {enabled: newValue, section: "verdict"})
        .error(util.showSavedIndicator)
        .call();
    }
  };

  self.toggleSuomifiNeighbors = function() {
    var newValue = !self.suomifiMessagesNeighborsEnabled();
    self.suomifiMessagesNeighborsEnabled(newValue);
    if (self.initialized) {
      ajax.command("toggle-organization-suomifi-messages", {enabled: newValue, section: "neighbors"})
        .error(util.showSavedIndicator)
        .call();
    }
  };

  self.load = function() { ajax.query("organization-by-user").success(self.init).call(); };

  // Helper function for making checkboxes that save their status by a command
  var simpleComputed = function(observable, toJS, command, params, observableParamName) {
    self.disposedComputed(function() {
      params[observableParamName] = (toJS ? ko.toJS(observable) : observable());
      if (self.initialized) {
        save(command, params);
      }});};

  var verySimpleComputed = function(observable, command) {
    return simpleComputed(observable, false, command, {}, "enabled");
  };

  verySimpleComputed(self.appRequiredFieldsFillingObligatory, "set-organization-app-required-fields-filling-obligatory");
  verySimpleComputed(self.planInfoDisabled, "set-organization-plan-info-disabled");
  verySimpleComputed(self.automaticOkForAttachments, "set-automatic-ok-for-attachments");
  verySimpleComputed(self.automaticConstructionStarted, "set-automatic-construction-started");

  simpleComputed(self.assignmentsEnabled, false, "set-organization-assignments", {}, "enabled");

  simpleComputed(self.suomifiMessagesVerdictEnabled, false, "toggle-organization-suomifi-messages",
    {section: "verdict"}, "enabled");
  simpleComputed(self.suomifiMessagesNeighborsEnabled, false, "toggle-organization-suomifi-messages",
    {section: "neighbors"}, "enabled");
  simpleComputed(self.suomifiMessagesVerdictMessage, false, "set-organization-suomifi-message",
    {section: "verdict"}, "message");
  simpleComputed(self.suomifiMessagesNeighborsMessage, false, "set-organization-suomifi-message",
    {section: "neighbors"}, "message");

  simpleComputed(self.ramDisabled, false, "toggle-organization-ram", {}, "disabled");
  simpleComputed(self.ramMessage, true, "set-organization-ram-message", {}, "message");

  ko.computed(function() {
    var inspectionSummariesEnabled = self.inspectionSummariesEnabled();
    if (self.initialized) {
      ajax.command("set-organization-inspection-summaries", {enabled: inspectionSummariesEnabled})
        .success(function(event) {
          util.showSavedIndicator(event);
          if (inspectionSummariesEnabled) {
            lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();
          }
        })
        .error(util.showSavedIndicator)
        .call();
    }
  });

  verySimpleComputed(self.extendedConstructionWasteReportEnabled, "set-organization-extended-construction-waste-report");
  verySimpleComputed(self.multipleOperationsSupported, "set-organization-multiple-operations-support");
  verySimpleComputed(self.noCommentNeighborAttachmentEnabled, "set-organization-no-comment-neighbor-attachment-enabled");
  verySimpleComputed(self.removeHandlersFromRevertedDraft, "set-organization-remove-handlers-from-reverted-draft");
  verySimpleComputed(self.removeHandlersFromConvertedApplication, "set-organization-remove-handlers-from-converted-application");
  verySimpleComputed(self.foremanTerminationRequestEnabled, "set-organization-foreman-termination-request-enabled");
  verySimpleComputed(self.validateVerdictGivenDate, "set-organization-validate-verdict-given-date");
  verySimpleComputed(self.automaticReviewFetchEnabled, "set-organization-review-fetch-enabled");
  verySimpleComputed(self.onlyUseInspectionFromBackend, "set-only-use-inspection-from-backend");
  verySimpleComputed(self.useAttachmentLinksIntegration, "set-organization-use-attachment-links-integration");

  self.validateVerdictGivenDateVisible = self.disposedPureComputed(function() {
    var types = self.permitTypes();
    return _.includes(types, "R") || _.includes(types, "P");
  });

  self.reviewFetchTogglerVisible = self.disposedPureComputed(function() {
    return _.includes(self.permitTypes(), "R");
  });

  function toAttachments(attachments) {
    return _(attachments || [])
      .map(function(a) { return {id: a, text: loc(["attachmentType", a[0], a[1]])}; })
      .sortBy("text")
      .value();
  }

  self.neighborOrderEmails = ko.observable("");
  simpleComputed(self.neighborOrderEmails, false, "set-organization-neighbor-order-email", {}, "emails");

  self.submitNotificationEmails = ko.observable("");
  simpleComputed(self.submitNotificationEmails, false, "set-organization-submit-notification-email", {}, "emails");

  self.infoRequestNotificationEmails = ko.observable("");
  simpleComputed(self.infoRequestNotificationEmails, false, "set-organization-inforequest-notification-email", {}, "emails");

  self.fundingNotificationEmails = ko.observable("");
  simpleComputed(self.fundingNotificationEmails, false, "set-organization-funding-enabled-notification-email", {}, "emails");

  // Reacting to changes in the handling time field or enable checkbox
  self.disposedComputed(function() {
    var days = self.handlingTimeEnabled() ? (self.handlingTime() || "30") : "0"; // 0 = disabled
    if (self.initialized) {
      save("set-organization-handling-time", {days: _.parseInt(days)});
    }});

  ko.computed(function() {
    var startDate = self.permanentArchiveInUseSince();
    if (self.initialized && startDate) {
      ajax.command("set-organization-permanent-archive-start-date", {date: startDate.getTime()})
        .success(util.showSavedIndicator)
        .error(function(res) {
          util.showSavedIndicator(res);
          if (res.text === "error.invalid-date") {
            self.permanentArchiveInUseSince(null);
          }
        })
        .call();
    }
  });


  function sanitizeLocation( z ) {
    return _.replace( _.replaceAll( z, /\s+/, "" ), ",", "." );
  }

  self.defaultDigitalizationLocationX = ko.observable("");
  self.defaultDigitalizationLocationY = ko.observable("");
  ko.computed(function() {
    var x = self.defaultDigitalizationLocationX();
    var y = self.defaultDigitalizationLocationY();
    if (self.initialized) {
      save("set-default-digitalization-location",
        {x: sanitizeLocation( x ), y: sanitizeLocation( y )});
    }
  });

  function setTosFunctionForOperation(operationId, functionCode) {
    var cmd = functionCode !== null ? "set-tos-function-for-operation" : "remove-tos-function-from-operation";
    var data = {operation: operationId};
    if (functionCode !== null) {
      data.functionCode = functionCode;
    }
    save(cmd, data);
  }

  ko.computed(function() {
    var tosFunction = self.archivingProjectTosFunction();
    if (self.initialized) {
      setTosFunctionForOperation("archiving-project", tosFunction);
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
          save( "section-toggle-enabled", {flag: sectionEnabled()});
        }
      }
    });

  hub.subscribe("inspectionSummaryService::templatesLoaded", function(event) {
    self.inspectionSummaryTemplates(event.templates);
    self.operationsInspectionSummaryTemplates(_.get(event, "operations-templates"));
  });


  // Pate verdict templates
  self.pateEnabled = ko.observable( false );

  self.selectableVerdictTemplates = ko.observable( {} );
  self.defaultOperationVerdictTemplates = ko.observable( {} );
  self.pateSupported = function( permitTypeOrOperation ) {
    return Boolean( self.selectableVerdictTemplates()[permitTypeOrOperation]);
  };

  function refreshVerdictTemplates() {
    ajax.query( "selectable-verdict-templates",
                {"org-id": self.organizationId()})
    .success( function( res ) {
      self.selectableVerdictTemplates( _.get( res, "items", {} ));
    })
    .call();
    ajax.query( "default-operation-verdict-templates",
                {"org-id": self.organizationId()})
    .success( function( res ) {
      self.defaultOperationVerdictTemplates( _.get( res, "templates", {} )  );
    })
    .call();
  }

  self.pateEnabled.subscribe( function( enabled ) {
    if( enabled ) {
      refreshVerdictTemplates();
    }
  });

  // Sent from pate/service.cljs
  hub.subscribe( "pate::verdict-templates-changed", refreshVerdictTemplates );

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

      ajax.query("pate-enabled")
      .success( _.wrap( true, self.pateEnabled))
      .error( _.noop )
      .call();

    // Required fields in app obligatory to submit app

    self.appRequiredFieldsFillingObligatory(organization["app-required-fields-filling-obligatory"] || false);

    self.planInfoDisabled(organization["plan-info-disabled"] || false);

    self.assignmentsEnabled(organization["assignments-enabled"] || false);

    self.automaticOkForAttachments(organization["automatic-ok-for-attachments-enabled"] || false);

    if (_.has(organization, "suomifi-messages.verdict.message")) {
      self.suomifiMessagesVerdictMessage(_.get(organization, "suomifi-messages.verdict.message"));
    }

    if (_.has(organization, "suomifi-messages.neighbors.message")) {
      self.suomifiMessagesNeighborsMessage(_.get(organization, "suomifi-messages.neighbors.message"));
    }

    if (_.has(organization, "ram.disabled")) {
      self.ramDisabled(_.get(organization, "ram.disabled"));
    }

    if (_.has(organization, "ram.message")) {
      for (var i in organization.ram.message) {
        if (_.has(organization.ram.message, i)) {
          self.ramMessage[i](organization.ram.message[i]);
        }
      }
    }

    self.extendedConstructionWasteReportEnabled(organization["extended-construction-waste-report-enabled"] || false);

    self.multipleOperationsSupported(organization["multiple-operations-supported"] || false);
    self.noCommentNeighborAttachmentEnabled(organization["no-comment-neighbor-attachment-enabled"] || false);

    var handlingTime = organization["handling-time"];
    self.handlingTime(handlingTime && handlingTime.days || 30);
    self.handlingTimeEnabled(handlingTime && handlingTime.enabled);

    self.removeHandlersFromRevertedDraft(organization["remove-handlers-from-reverted-draft"] || false);
    self.removeHandlersFromConvertedApplication(organization["remove-handlers-from-converted-application"] || false);

    self.foremanTerminationRequestEnabled(organization["foreman-termination-request-enabled"] || false);

    self.validateVerdictGivenDate(organization["validate-verdict-given-date"] === true);

    self.automaticConstructionStarted(organization["automatic-construction-started"] !== false);
    self.automaticReviewFetchEnabled(organization["automatic-review-fetch-enabled"] === true);
    self.onlyUseInspectionFromBackend(organization["only-use-inspection-from-backend"] || false);

    self.permanentArchiveEnabled(organization["permanent-archive-enabled"] || false);
    self.permanentArchiveInUseSince(new Date(organization["permanent-archive-in-use-since"] || 0));
    var earliestArchivingTs = organization["earliest-allowed-archiving-date"];
    if (earliestArchivingTs > 0) {
      self.earliestArchivingDate(new Date(earliestArchivingTs));
    }

    self.reviewOfficersListEnabled(organization["review-officers-list-enabled"] || false);

    self.inspectionSummariesEnabled(organization["inspection-summaries-enabled"] || false);

    if (organization["inspection-summaries-enabled"]) {
      lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();
    }

    self.useAttachmentLinksIntegration(organization["use-attachment-links-integration"] === true);

    // Operation attachments
    //
    var operationsAttachmentsPerPermitType = organization.operationsAttachments || {};
    var localizedOperationsAttachmentsPerPermitType = [];

    self.langs(_.keys(organization.name));
    self.links(organization.links || []);

    var operationsTosFunctions = organization["operations-tos-functions"] || {};

    self.archivingProjectTosFunction(operationsTosFunctions["archiving-project"]);

    self.neighborOrderEmails(util.getIn(organization, ["notifications", "neighbor-order-emails"], []).join("; "));
    self.submitNotificationEmails(util.getIn(organization, ["notifications", "submit-notification-emails"], []).join("; "));
    self.infoRequestNotificationEmails(util.getIn(organization, ["notifications", "inforequest-notification-emails"], []).join("; "));
    self.fundingNotificationEmails(util.getIn(organization, ["notifications", "funding-notification-emails"], []).join("; "));

    self.defaultDigitalizationLocationX(util.getIn(organization, ["default-digitalization-location", "x"], []));
    self.defaultDigitalizationLocationY(util.getIn(organization, ["default-digitalization-location", "y"], []));

    self.stateChangeMsgEnabled(organization["state-change-msg-enabled"] || false);

    self.stateChangeConf( {
      url: util.getIn(organization, ["state-change-endpoint", "url"], []),
      headers: util.getIn(organization, ["state-change-endpoint", "header-parameters"], []),
      authType: util.getIn(organization, ["state-change-endpoint", "auth-type"], []),
      username: util.getIn(organization, ["state-change-endpoint", "basic-auth-username"], []),
      password: util.getIn(organization, ["state-change-endpoint", "basic-auth-password"], [])
    });

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

    self.attachmentTypeSettings = data["operation-attachment-settings"];
    self.attachmentTypesDocdepartmental = data.attachmentTypesDocdepartmental;

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

    self.handlerRoles( _.get( organization, "handler-roles", []));

    self.automaticAssignmentFilters(_.get(organization, "automatic-assignment-filters", []));

    self.noticeForms( _.get( organization, "notice-forms", {}));

    self.reviewPdf( _.get( organization, "review-pdf", {}));

    defaultAttachmentsMandatory( _.reduce( _.get( organization, "default-attachments-mandatory"),
                                           function( acc, op ) {
                                             return _.set( acc, op, true );
                                           },
                                           {}));

    if (_.has(organization, "export-files")) {
      self.attachmentExportFiles(_.map(
        organization["export-files"],
        function (file) {
          return {
            filename: file.filename,
            url:      "/api/raw/download-organization-attachments-export-file?organizationId=" + organization.id + "&fileId=" + file.fileId,
            size:     util.sizeString(file.size),
            created:  util.finnishDateAndTime(file.created)
          };}));
    } else {
      self.attachmentExportFiles([]);
    }

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

  function linksForCommand(links) {
    return _.reduce(links, function (acc, link) {
      acc.url[link.lang] = link.url();
      acc.name[link.lang] = link.name();
      return acc;
    }, {url: {}, name: {}});
  }

  self.editLink = function(indexFn) {
    var index = indexFn();
    self.editLinkModel.init({
      source: this,
      langs: self.langs(),
      commandName: "edit",
      command: function(links) {
        ajax
          .command("update-organization-link", _.merge({index: index},
                                                       linksForCommand(links)))
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
      langs: self.langs(),
      command: function(links) {
        ajax
          .command("add-organization-link", linksForCommand(links))
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
      .command("remove-organization-link", {url: this.url, name: this.name})
      .success(self.load)
      .call();
  };

  self.openLinkDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-link");
  };
};
