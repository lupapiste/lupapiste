(function() {
  "use strict";

  var organizationModel,
      organizationUsers,
      editSelectedOperationsModel,
      editAttachmentsModel,
      wfsModel,
      statementGiversModel,
      createStatementGiverModel,
      editStatementGiverModel,
      reviewOfficersModel,
      createReviewOfficerModel,
      editReviewOfficerModel,
      kopiolaitosModel,
      asianhallintaModel,
      linkToVendorBackendModel,
      usersList = null,
      calendarsModel,
      reservationTypesModel,
      reservationPropertiesModel,
      bulletinsModel,
      docterminalModel,
      docdepartmentalModel;

  function toAttachmentData(groupId, attachmentId) {
    return {
      id:   {"type-group": groupId, "type-id": attachmentId},
      text: loc(["attachmentType", groupId, attachmentId])
    };
  }

  var mapToSelectmOptions = function(attachmentTypes) {
   return _.map(attachmentTypes, function (g) {
      var groupId = g[0],
          groupText = loc(["attachmentType", groupId, "_group_label"]),
          attachmentIds = g[1],
          attachments = _.map(attachmentIds, _.partial(toAttachmentData, groupId));
      return [groupText, attachments];
    });
  };

  function getAllOrganizationAttachmentTypesForPermitType(permitType) {
    var defaultAllowedTypesMap = organizationModel.attachmentTypeSettings.defaults["allowed-attachments"][permitType];
    var defaultAllowedTypes = _.map(defaultAllowedTypesMap, function (files, group) { return [group, files];});
    return mapToSelectmOptions(defaultAllowedTypes);
  }

  function EditAttachmentsModel() {
    var self = this;
    var dialogId = "#dialog-edit-attachments";

    self.op = ko.observable();
    self.opName = ko.computed(function() { var o = self.op(); return o && o.text; });

    self.open = function(op) {
      self.op(op);
      self.selectm.reset(
          getAllOrganizationAttachmentTypesForPermitType(op.permitType),
          _.map(op.attachments, function(a) {
            return toAttachmentData.apply(null, a.id);
          }));
      LUPAPISTE.ModalDialog.open(dialogId);
      return self;
    };

    self.execute = function(attachments) {
      var handler = setTimeout(function() { $(dialogId).ajaxMaskOn(); }, 200);
      ajax.command("organization-operations-attachments", {
          operation: self.op().id,
          attachments: _.map(attachments, function(a) { return [a["type-group"], a["type-id"]]; })
        })
        .success(organizationModel.load)
        .complete(function() {
          clearTimeout(handler);
          LUPAPISTE.ModalDialog.close();
          $(dialogId).ajaxMaskOff();
        })
        .call();
    };

    $(function() {
      self.selectm = $(dialogId +  " .attachment-templates").selectm(null, "edit-attachments");
      self.selectm
        .ok(self.execute)
        .cancel(LUPAPISTE.ModalDialog.close);
    });
  }

  function toOperationData(operationId) {
    return {
      id:   operationId,
      text: loc(["operations", operationId])
    };
  }

  function getAllSelectableOperationsForPermitTypes(operations) {
    return _.map(operations, function(op) {

      var resolveOpIdFromTree = function(pathPart) {
        if (_.isArray(pathPart[1])) {
          var reducable = _.isString(pathPart[0]) ? pathPart[1] : pathPart;
          var opsArray = _.reduce(reducable, function(ops, path) {
            return ops.concat( resolveOpIdFromTree(path) );
          }, []);
          return opsArray;
        } else {
          return pathPart[1];
        }
      };

      var groupText = loc(["operations.tree", op[0]]);
      var operationIds = _.isArray(op[1]) ? resolveOpIdFromTree(op[1]) : op[1];

      operationIds = _.isArray(operationIds) ? operationIds : [operationIds];
      var operationDatas = _.map(operationIds, toOperationData);

      return [groupText, operationDatas];
    });
  }

  function selectedOperationsForSelectM(operations) {
    var arr = _.reduce(operations, function(arr, selOp) {
      var temp = _.map(selOp.operations, function(op) {
        return toOperationData(op.id);
      });
      return arr.concat(temp);
    }, []);
    return arr;
  }

  function EditSelectedOperationsModel() {
    var self = this;
    var dialogId = "#dialog-edit-selected-operations";

    self.open = function(organization) {
      self.selectm.reset(
          // "source data"
          getAllSelectableOperationsForPermitTypes(organization.allOperations),
          // "target data"
          selectedOperationsForSelectM(organization.selectedOperations())
          );
      LUPAPISTE.ModalDialog.open(dialogId);
      return self;
    };

    self.execute = function(operations) {
      var handler = setTimeout(function() { $(dialogId).ajaxMaskOn(); }, 200);
      ajax.command("set-organization-selected-operations", {operations: operations})
        .success(function( response ) {
          organizationModel.load( response );
          hub.send( "organization-selected-operations" );
        })
        .complete(function() {
          clearTimeout(handler);
          LUPAPISTE.ModalDialog.close();
          $(dialogId).ajaxMaskOff();
        })
        .call();
    };

    $(function() {
      self.selectm = $(dialogId + " .selected-operation-templates").selectm(null, "edit-selected-operations");
      self.selectm
        .ok(self.execute)
        .cancel(LUPAPISTE.ModalDialog.close);
    });
  }

  function PersonListModel(listed, createListPersonModel, editListPersonModel) {
    var self = this;

    self.data = ko.observableArray();

    self.modelCreate = createListPersonModel;
    self.modelEdit = editListPersonModel;

    self.load = function() {
      // These comments here for the benefit of anyone grepping for the queries below:
      // .query("get-organizations-statement-givers")
      // .query("get-organizations-review-officers")
      ajax
        .query("get-organizations-" + listed + "s")
        .success(function(result) { self.data(ko.mapping.fromJS(result.data)); })
        .call();
    };

    self.deletePerson = function() {
      var person = this;
      hub.send( "show-dialog", {ltitle: "areyousure",
                                size: "medium",
                                component: "yes-no-dialog",
                                componentParams: {text: loc( "person-list.delete.confirmation",
                                                             person.name() ),
                                                  yesFn: function() {
                                                    ajax.command("delete-" + listed,
                                                                 {personId: person.id()})
                                                      .success(self.load)
                                                      .call();
                                                  },
                                                  lyesTitle: "yes",
                                                  lnoTitle: "no"}});

    };

    self.openCreateModal = function() {
      createListPersonModel.copyFrom({});
      createListPersonModel.error(false);
      LUPAPISTE.ModalDialog.open("#dialog-create-" + listed);
    };

    self.openEditModal = function() {
      editListPersonModel.copyFrom(this);
      editListPersonModel.error(false);
      LUPAPISTE.ModalDialog.open("#dialog-edit-" + listed);
    };

    self.load();
  }

  function statementGiverValidator(model) {
    return function() { return _.trim(model.email()) && _.trim(model.text()); };
  }

  function reviewOfficerValidator(model) {
    return function() { return _.trim(model.code()) && _.trim(model.name()); };
  }

  function StatementGiversModel() {
    return new PersonListModel("statement-giver", createStatementGiverModel, editStatementGiverModel);
  }

  function ReviewOfficersModel() {
    return new PersonListModel("review-officer", createReviewOfficerModel, editReviewOfficerModel);
  }

  function statementGiversModelGetter() {
    return statementGiversModel;
  }

  function reviewOfficersModelGetter() {
    return reviewOfficersModel;
  }

  function PersonListModalViewModel(commandString, fields, validation, modelGetter) {
    var self = this;

    // Set members
    for (var i = 0; i < fields.length; i += 1) {
      var field = fields[i];
      self[field] = ko.observable();
    }

    self.error = ko.observable();
    self.command = ko.observable();
    self.formOk = ko.computed(validation(self));


    // Import data from a variable
    self.copyFrom = function(data) {
      if (_.has(data, "id")) {
        self.personId = data.id;
      }
      for (var i = 0; i < fields.length; i += 1) {
        var field = fields[i];
        self[field](util.getIn(data, [field], ""));
      }
      return self;
    };

    // Called when successfully saved
    self.onSuccess = function() {
      modelGetter().load();
      self.error(null);
      LUPAPISTE.ModalDialog.close();
    };

    // Save the form's data
    self.save = function() {
      var person = ko.mapping.toJS(self);
      ajax.command(commandString, person)
        .success(self.onSuccess)
        .error(function(result) {
          self.error(result.text);
        })
        .call();
      return false;
    };
  }

  function CreateStatementGiverModel() {
    return new PersonListModalViewModel("create-statement-giver",
                                        ["name", "email", "text"],
                                        statementGiverValidator,
                                        statementGiversModelGetter);
  }

  function CreateReviewOfficerModel() {
    return new PersonListModalViewModel("create-review-officer",
                                        ["name", "code"],
                                        reviewOfficerValidator,
                                        reviewOfficersModelGetter);
  }

  function EditStatementGiverModel() {
    return new PersonListModalViewModel("edit-statement-giver",
                                        ["name", "email", "text"],
                                        statementGiverValidator,
                                        statementGiversModelGetter);
  }

  function EditReviewOfficerModel() {
    return new PersonListModalViewModel("edit-review-officer",
                                        ["name", "code"],
                                        reviewOfficerValidator,
                                        reviewOfficersModelGetter);
  }

  function KopiolaitosModel() {
    var self = this;

    self.error                         = ko.observable(false);
    self.kopiolaitosEmail              = ko.observable("");
    self.kopiolaitosEmailTemp          = ko.observable("");
    self.kopiolaitosOrdererAddress     = ko.observable("");
    self.kopiolaitosOrdererAddressTemp = ko.observable("");
    self.kopiolaitosOrdererEmail       = ko.observable("");
    self.kopiolaitosOrdererEmailTemp   = ko.observable("");
    self.kopiolaitosOrdererPhone       = ko.observable("");
    self.kopiolaitosOrdererPhoneTemp   = ko.observable("");

    self.ok = ko.computed(function() {
      return (_.isEmpty(self.kopiolaitosEmailTemp()) || util.isValidEmailAddress(self.kopiolaitosEmailTemp())) &&
             (_.isEmpty(self.kopiolaitosOrdererEmailTemp()) || util.isValidEmailAddress(self.kopiolaitosOrdererEmailTemp()));
    });

    self.load = function() {
      ajax.query("kopiolaitos-config")
        .success(function(d) {
          self.kopiolaitosEmail(d["kopiolaitos-email"] || "");
          self.kopiolaitosOrdererAddress(d["kopiolaitos-orderer-address"] || "");
          self.kopiolaitosOrdererPhone(d["kopiolaitos-orderer-phone"] || "");
          self.kopiolaitosOrdererEmail(d["kopiolaitos-orderer-email"] || "");
        })
        .call();
    };

    self.save = function() {
      ajax.command("set-kopiolaitos-info", {kopiolaitosEmail: _.trim(self.kopiolaitosEmailTemp()),
                                            kopiolaitosOrdererAddress: _.trim(self.kopiolaitosOrdererAddressTemp()),
                                            kopiolaitosOrdererPhone: _.trim(self.kopiolaitosOrdererPhoneTemp()),
                                            kopiolaitosOrdererEmail: _.trim(self.kopiolaitosOrdererEmailTemp())})
        .success(function() {
          self.load();
          self.error(false);
          LUPAPISTE.ModalDialog.close();
        })
        .error(function(e) {
          self.error(e.text);
        })
        .call();
      return false;
    };

    self.openDialog = function() {
      self.error(false);
      self.kopiolaitosEmailTemp( self.kopiolaitosEmail() );
      self.kopiolaitosOrdererAddressTemp( self.kopiolaitosOrdererAddress() );
      self.kopiolaitosOrdererEmailTemp( self.kopiolaitosOrdererEmail() );
      self.kopiolaitosOrdererPhoneTemp( self.kopiolaitosOrdererPhone() );
      LUPAPISTE.ModalDialog.open("#dialog-edit-kopiolaitos-info");
    };

  }


  function AsianhallintaModel() {
    var self = this;

    self.indicator = ko.observable().extend({notify: "always"});

    self.configs = ko.observableArray();
    self.versions = ko.observable(LUPAPISTE.config.asianhallintaVersions);

    var filterScope = function(item) {
      if (item.caseManagement && item.caseManagement.ftpUser) {
        return item;
      }
    };

    self.load = function() {
      var save = function(item) {
        ajax.command("save-asianhallinta-config",
                    {permitType: item.permitType(),
                     municipality: item.municipality(),
                     enabled: item.caseManagement.enabled(),
                     version: item.caseManagement.version()})
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
      };

      ajax.query("asianhallinta-config")
        .success(function (d) {
          self.configs(_.map(_.filter(d.scope, filterScope), function(item) {
            var configItem = ko.mapping.fromJS(item, {
              "caseManagement": {
                create: function(mappingItem) {
                  return {ftpUser: ko.observable(mappingItem.data.ftpUser),
                          enabled: _.has(mappingItem.data, "enabled") ? ko.observable(mappingItem.data.enabled) : ko.observable(false),
                          version: _.has(mappingItem.data, "version") ? ko.observable(mappingItem.data.version) : ko.observable(_.last(self.versions()))};
                }
              }
            });
            configItem.caseManagement.version.subscribe(function() {
              save(configItem);
            });
            configItem.caseManagement.enabled.subscribe(function() {
              save(configItem);
            });
            return configItem;
          }));
        }).call();
    };

  }

  function LinkToVendorBackendModel() {
    var self = this;

    function commandCallbacks(indicator) {
      return {
        onSuccess: function () { indicator({type: "saved"}); },
        onError: function () { indicator({type: "err"}); }
      };
    }

    function saveFunc(key, indicator) {
      var callbacks = commandCallbacks(indicator);
      return _.debounce(function (val) {
        ajax
          .command("save-vendor-backend-redirect-config", {key: key, val: val})
          .success(callbacks.onSuccess || _.noop)
          .error(callbacks.onError || _.noop)
          .fail(callbacks.onFail || _.noop)
          .call();
      }, 1500);
    }

    self.backendIdIndicator = ko.observable().extend({notify: "always"});
    self.vendorBackendUrlForBackendId = ko.observable("");

    self.LpIdIndicator = ko.observable().extend({notify: "always"});
    self.vendorBackendUrlForLpId = ko.observable("");

    self.subscribe = function() {
      self.vendorBackendUrlForBackendId.subscribe(
        saveFunc("vendorBackendUrlForBackendId", self.backendIdIndicator)
      );
      self.vendorBackendUrlForLpId.subscribe(
        saveFunc("vendorBackendUrlForLpId", self.LpIdIndicator)
      );
    };

    self.load = function() {
      ajax.query("vendor-backend-redirect-config")
        .success(function(config) {
          self.vendorBackendUrlForBackendId(config["vendor-backend-url-for-backend-id"] || "");
          self.vendorBackendUrlForLpId(config["vendor-backend-url-for-lp-id"] || "");
          self.subscribe();
        })
        .call();
    };
  }

  function BulletinsModel() {
    // Model for the organization-bulletins page, functionality for maintaining (non-YMP) bulletin settings for organization
    var self = this;

    self.scopes = ko.observableArray();
    self.texts = ko.observable();
    self.textsInitialized = ko.observable(false);
    self.canEdit = ko.computed( _.wrap("update-organization-bulletin-scope",
                                       lupapisteApp.models.globalAuthModel.ok));

    self.saveScope = function(data) {
      ajax
        .command("update-organization-bulletin-scope", data)
        .success(util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    };

    function upsertText(data) {
      ajax
        .command("upsert-organization-local-bulletins-text", data)
        .success(data.onSuccess || util.showSavedIndicator)
        .error(util.showSavedIndicator)
        .call();
    }

    function newCaptionTextModel(lang, text, index) {
      var obs = ko.observable(text);
      obs.subscribe( function( value ) {
        upsertText({lang: lang, key: "caption", index: index, value: value});
      });

      return {text: obs, doRemove: _.partial(removeCaption, lang, index)};
    }

    self.appendCaption = function(lang) {
      var caption = _.get(self.texts(), [lang, "caption"]);
      if (caption) {
        var captionArr = caption();
        upsertText({lang: lang, key: "caption", index: captionArr.length, value: "", onSuccess: self.load});
      }
    };

    function doRemoveCaption(lang, idx) {
      ajax.command("remove-organization-local-bulletins-caption", {lang: lang, index: idx})
          .success(self.load)
          .error(util.showSavedIndicator)
          .call();
    }

    function removeCaption(lang, idx) {
      hub.send("show-dialog", {ltitle: "areyousure",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "auth-admin.local-bulletins-page-texts.caption-confirm-remove",
                                                 yesFn: _.partial(doRemoveCaption, lang, idx)}});
    }

    self.load = function() {
      ajax.query("user-organization-bulletin-settings")
        .success(function(data) {
          self.scopes(_.map(data["bulletin-scopes"], function(setting) {
            _.merge(setting, {notificationEmail: ko.observable(_.get(setting, ["bulletins", "notification-email"])),
                              descriptionsFromBackendSystem: ko.observable(_.get(setting, ["bulletins", "descriptions-from-backend-system"]))});
            setting.notificationEmail.subscribe(function(s) {
              self.saveScope({permitType: setting.permitType,
                              municipality: setting.municipality,
                              notificationEmail: s});
            });
            setting.descriptionsFromBackendSystem.subscribe(function(s) {
              self.saveScope({permitType: setting.permitType,
                              municipality: setting.municipality,
                              descriptionsFromBackendSystem: s}); });
            return setting;
          }));

          var koMappedTexts = ko.mapping.fromJS(data["local-bulletins-page-texts"]);

          _.map(loc.getSupportedLanguages(), function(lang) {
            _.map(["heading1", "heading2"], function(key) {
              var obs = _.get(koMappedTexts, [lang, key]);
              if (obs) {
                obs.subscribe( function( value ) {
                  upsertText({lang: lang, key: key, value: value});
                });
              }
            });

            // map the observableArray of String paragraphs into an observableArray of
            // observables with change listener functions inside
            var captionObsArray = _.get(koMappedTexts, [lang, "caption"]);
            if (captionObsArray) {
              captionObsArray(_.map(captionObsArray(), _.partial(newCaptionTextModel, lang)));
            }
          });

          self.texts(koMappedTexts);
          self.textsInitialized(true);
        })
        .call();
    };

  }

  function DocterminalModel(mode) {
    var self = this;
    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.data = ko.observableArray();

    self.canEdit = ko.pureComputed(  _.wrap("set-doc" + mode + "-attachment-type",
                                            lupapisteApp.models.globalAuthModel.ok));

    self.numberEnabled = ko.pureComputed(function() {
      var groups = self.data();
      return _.sumBy(groups, function(group) {
        return _.sumBy(group[1], function(typeEntry) {
          return typeEntry.enabled() ? 1 : 0;
        });
      });
    });

    self.toggleSelectText = ko.pureComputed(function() {
      return self.numberEnabled() === 0 ? "select-all" : "select-none";
    });

    function attachSave(typeEntry) {
      var clicked = false; // Prevent save when computed is run first time
      self.disposedComputed(function() {
        var enabled = typeEntry.enabled();
        if (clicked) {
          ajax
            .command("set-doc" + mode + "-attachment-type",
                     {attachmentType: typeEntry.type,
                      enabled: enabled})
            .success(util.showSavedIndicator)
            .error(util.showSavedIndicator)
            .call();
        } else {
          clicked = true;
        }
      });
    }

    self.load = function () {
      ajax.query("docterminal-attachment-types",
          {docMode: mode})
        .success(function(data) {
          self.data(_.map(data["attachment-types"], function(group) {
            return [group[0],
                    _.map(group[1], function(attachmentType) {
                      var typeEnabled = ko.observable(attachmentType.enabled);
                      var typeEntry = {type: attachmentType.type,
                                       enabled: typeEnabled};
                      attachSave(typeEntry);
                      return typeEntry;
                    })];
          }));
        })
        .call();
    };

    self.toggleAll = function() {
      var enableAll = self.numberEnabled() === 0;
      ajax
        .command("set-doc" + mode + "-attachment-type",
                 {attachmentType: "all", enabled: enableAll})
        .success(function(data) { self.load(); util.showSavedIndicator(data); })
        .error(util.showSavedIndicator)
        .call();
    };
  }

  // Needs to be a function to delay execution due to async issues
  var operationsItems = function() {
    return _(_.cloneDeep(organizationModel.selectedOperations()))
      .map(function (x) { return x.operations; })
      .flatten() // Now we have combined P, R, YA - operations in one array
      .map(function(item) {
        item.value = item.id;
        delete item.id;
        delete item.permitType;
        return item;
      })
      .value();
  };

  // Needs to be a function to delay execution due to async issues
  var partiesItems = function() {
    return _.map(
      ["hakija", "paasuunnittelija", "erityissuunnittelijat", "vastaava tyonjohtaja", "muut tyonjohtajat"],
      function(item) {
        return {value: item, text: loc("osapuoli.kategoria." + item)};
      });
  };

  organizationModel = new LUPAPISTE.OrganizationModel();
  organizationUsers = new LUPAPISTE.OrganizationUserModel(organizationModel);
  editSelectedOperationsModel = new EditSelectedOperationsModel();
  editAttachmentsModel = new EditAttachmentsModel();
  calendarsModel = new LUPAPISTE.AuthAdminCalendarsModel();
  reservationTypesModel = new LUPAPISTE.AuthAdminReservationTypesModel();
  reservationPropertiesModel = new LUPAPISTE.AuthAdminOrganizationReservationPropertiesModel();

  wfsModel = new LUPAPISTE.WFSModel();
  createStatementGiverModel = new CreateStatementGiverModel();
  editStatementGiverModel = new EditStatementGiverModel();
  statementGiversModel = new StatementGiversModel();
  createReviewOfficerModel = new CreateReviewOfficerModel();
  editReviewOfficerModel = new EditReviewOfficerModel();
  reviewOfficersModel = new ReviewOfficersModel();
  kopiolaitosModel = new KopiolaitosModel();
  asianhallintaModel = new AsianhallintaModel();
  linkToVendorBackendModel = new LinkToVendorBackendModel();
  bulletinsModel = new BulletinsModel();
  docterminalModel = new DocterminalModel("terminal");
  docdepartmentalModel = new DocterminalModel("departmental");

  var usersTableConfig = {
      hideRoleFilter: true,
      hideEnabledFilter: true,
      ops: [{name: "removeFromOrg",
             button: "secondary",
             icon: "lupicon-remove",
             showFor: _.partial(lupapisteApp.models.globalAuthModel.ok, "remove-user-organization"),
             operation: function(email, callback) {
               ajax
                 .command("remove-user-organization", {email: email})
                 .success(function() { callback(true); })
                 .call();
            }},
            {name: "editUser",
             button: "positive",
             icon: "lupicon-pen",
             showFor: _.partial(lupapisteApp.models.globalAuthModel.ok, "update-user-roles"),
             rowOperationFn: function (row) {
               pageutil.openPage("edit-authority", row.user._id);
             }}]
  };

  hub.subscribe("redraw-users-list", function() {
    if (usersList) {
      usersList.redraw();
    }
  });

  hub.onPageLoad("users", function() {
    if (!usersList) {
      usersList = users.create($("#users .admin-users-table"), usersTableConfig);
    } else {
      usersList.redraw();
    }
    statementGiversModel.load();
  });

  hub.onPageLoad("reviews", function() {
    reviewOfficersModel.load();
  });

  hub.onPageLoad("backends", function() {
    wfsModel.load();
    kopiolaitosModel.load();
    asianhallintaModel.load();
    linkToVendorBackendModel.load();
  });

  if (features.enabled("ajanvaraus")) {
    ko.computed(function() {
      var organizationId = organizationModel.organizationId();
      if (lupapisteApp.models.globalAuthModel.ok("calendars-enabled") && !_.isUndefined(organizationId)) {
        hub.send("calendarService::fetchOrganizationReservationTypes",
          {organizationId: organizationId});
      }
    });

    hub.onPageLoad("calendar-admin", function() {
      var path = pageutil.getPagePath();
      if (path.length > 1) {
        calendarsModel.userIdInView(path[0]);
        calendarsModel.calendarIdInView(path[1]);
        hub.send("calendarService::fetchCalendar", {user: path[0], calendarId: path[1],
                                                    calendarObservable: calendarsModel.calendarInView});
      }
    });

    hub.onPageLoad("organization-calendars", function() {
      hub.send("calendarService::fetchOrganizationCalendars");
      reservationPropertiesModel.load();
    });
  }

  hub.onPageLoad("organization-bulletins", function() {
    bulletinsModel.load();
  });

  hub.onPageLoad("organization-terminal-settings", function() {
    docterminalModel.load();
    docdepartmentalModel.load();
  });

  var authorityIdObservable = ko.observable("");

  hub.onPageLoad("edit-authority", function () {
      authorityIdObservable(_.head(pageutil.getPagePath()));
  });

  hub.onPageUnload("edit-authority", function () {
      authorityIdObservable("");
  });

  $(function() {
    organizationModel.load();
    $("#users").applyBindings({
      organization:       organizationModel,
      organizationUsers:  organizationUsers,
      statementGivers:    statementGiversModel
      });
    $("#applications").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel
    });
    $("#operations").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel,
      editSelectedOperationsModel: editSelectedOperationsModel
    });
    $("#attachments").applyBindings({
      organization:        organizationModel,
      editAttachments:     editAttachmentsModel,
    });
    $("#backends").applyBindings({
      organization:        organizationModel,
      wfs:                 wfsModel,
      kopiolaitos:         kopiolaitosModel,
      asianhallinta:       asianhallintaModel,
      linkToVendorBackend: linkToVendorBackendModel
    });
    $("#reviews").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel,
      reviewOfficers:      reviewOfficersModel
    });
    $("#areas").applyBindings({
      organization:        organizationModel
    });
    $("#reports").applyBindings({
      organization: organizationModel,
      reportsModel: new LUPAPISTE.OrganizationReportsModel()
    });
    $("#assignments").applyBindings({
      organization:        organizationModel,
      automatic: new LUPAPISTE.AutomaticAssignments( organizationModel ),
      authorization:       lupapisteApp.models.globalAuthModel
    });
    if (features.enabled("ajanvaraus")) {
      $("#organization-calendars").applyBindings({
        calendars:           calendarsModel,
        reservationTypes:    reservationTypesModel,
        reservationProperties: reservationPropertiesModel
      });
      $("#calendar-admin").applyBindings({
        calendars:           calendarsModel,
        reservationTypes:    reservationTypesModel
      });
    }
    $("#stamp-editor").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel
    });
    $("#automatic-emails").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel,
      partiesItems:        partiesItems,
      operationsItems:     operationsItems
    });
    $("#price-catalogue").applyBindings({
        organization:        organizationModel,
        authorization:       lupapisteApp.models.globalAuthModel
    });
    $("#pate-verdict-templates").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel
    });

    $("#archiving").applyBindings({
      organization:            organizationModel,
      authorization:           lupapisteApp.models.globalAuthModel
    });

    $("#organization-bulletins").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel,
      bulletins:           bulletinsModel
    });
    $("#organization-terminal-settings").applyBindings({
      organization:                   organizationModel,
      authorization:                  lupapisteApp.models.globalAuthModel,
      attachmentTypes:                docterminalModel,
      attachmentTypesDocdepartmental: docdepartmentalModel
    });
    $("#edit-authority").applyBindings({
        authorityIdObservable: authorityIdObservable,
        backToUsers: function () {
          pageutil.openPage("users");
        }
    });
    $("#organization-store-settings").applyBindings({
      organization: organizationModel
    });
    $("#ad-login-settings").applyBindings({
        organization: organizationModel,
        authorization: lupapisteApp.models.globalAuthModel
    });


    // Init the dynamically created dialogs
    LUPAPISTE.ModalDialog.init();
  });

})();
