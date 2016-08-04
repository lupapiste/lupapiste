(function() {
  "use strict";

  var organizationModel,
      organizationUsers,
      editSelectedOperationsModel,
      editAttachmentsModel,
      wfsModel,
      statementGiversModel,
      createStatementGiverModel,
      kopiolaitosModel,
      asianhallintaModel,
      linkToVendorBackendModel,
      usersList = null,
      editRolesDialogModel,
      calendarsModel,
      reservationTypesModel,
      reservationPropertiesModel;

  function toAttachmentData(groupId, attachmentId) {
    return {
      id:   {"type-group": groupId, "type-id": attachmentId},
      text: loc(["attachmentType", groupId, attachmentId])
    };
  }

  function getAllOrganizationAttachmentTypesForOperation(permitType, operation) {
    return _.map(organizationModel.attachmentTypes[permitType][operation], function(g) {
      var groupId = g[0],
          groupText = loc(["attachmentType", groupId, "_group_label"]),
          attachmentIds = g[1],
          attachments = _.map(attachmentIds, _.partial(toAttachmentData, groupId));
      return [groupText, attachments];
    });
  }

  function EditAttachmentsModel() {
    var self = this;
    var dialogId = "#dialog-edit-attachments";

    self.op = ko.observable();
    self.opName = ko.computed(function() { var o = self.op(); return o && o.text; });

    self.open = function(op) {
      self.op(op);
      self.selectm.reset(
          getAllOrganizationAttachmentTypesForOperation(op.permitType, op.id),
          _.map(op.attachments, function(a) {
            return toAttachmentData.apply(null, a.id);
          }));
      LUPAPISTE.ModalDialog.open(dialogId);
      return self;
    };

    self.execute = function(attachments) {
      var handler = setTimeout(function() { $(dialogId).ajaxMaskOn(); }, 200);
      ajax.command("organization-operations-attachments")
        .json({operation: self.op().id, attachments: _.map(attachments, function(a) { return [a["type-group"], a["type-id"]]; })})
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
      ajax.command("set-organization-selected-operations")
        .json({operations: operations})
        .success(organizationModel.load)
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

  function StatementGiversModel() {
    var self = this;

    self.data = ko.observableArray();

    self.load = function() {
      ajax
        .query("get-organizations-statement-givers")
        .success(function(result) { self.data(ko.mapping.fromJS(result.data)); })
        .call();
    };

    self["delete"] = function() {
      ajax
        .command("delete-statement-giver", {personId: this.id()})
        .success(self.load)
        .call();
    };

    self.openCreateModal = function()      {
      createStatementGiverModel.copyFrom({});
      LUPAPISTE.ModalDialog.open("#dialog-create-statement-giver");
    };
  }

  function CreateStatementGiverModel() {
    var self = this;

    self.email = ko.observable();
    self.email2 = ko.observable();
    self.text = ko.observable();
    self.error = ko.observable();
    self.command = ko.observable();
    self.disabled = ko.computed(function() {
      var emailOk = self.email() && util.isValidEmailAddress(self.email()) && self.email() === self.email2();
      var textOk = self.text();
      return !(emailOk && textOk);
    });

    self.copyFrom = function(data) {
      self.email(data.email);
      self.email2( data.email);
      self.text(data.text);
      return self;
    };

    self.onSuccess = function() {
      statementGiversModel.load();
      self.error(null);
      LUPAPISTE.ModalDialog.close();
    };

    self.save = function(data) {
      var statementGiver = ko.mapping.toJS(data);
      ajax.command("create-statement-giver", statementGiver)
        .success(self.onSuccess)
        .error(function(result) {
          var errorText = result.text;
          if (errorText === "error.user-not-found") {
            // Create user and retry
            ajax
              .command("create-user", {email: data.email(), role: "authority", lastName: data.email()})
              .success(function() {self.save(data);})
              .error(function(e){self.error(e.text);})
              .call();
          } else {
            self.error(errorText);
          }
        })
        .call();
      return false;
    };

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

  organizationModel = new LUPAPISTE.OrganizationModel();
  organizationUsers = new LUPAPISTE.OrganizationUserModel(organizationModel);
  editSelectedOperationsModel = new EditSelectedOperationsModel();
  editAttachmentsModel = new EditAttachmentsModel();

  calendarsModel = new LUPAPISTE.AuthAdminCalendarsModel();
  reservationTypesModel = new LUPAPISTE.AuthAdminReservationTypesModel();
  reservationPropertiesModel = new LUPAPISTE.AuthAdminOrganizationReservationPropertiesModel();

  wfsModel = new LUPAPISTE.WFSModel();
  statementGiversModel = new StatementGiversModel();
  createStatementGiverModel = new CreateStatementGiverModel();
  kopiolaitosModel = new KopiolaitosModel();
  asianhallintaModel = new AsianhallintaModel();
  linkToVendorBackendModel = new LinkToVendorBackendModel();
  editRolesDialogModel = new LUPAPISTE.EditRolesDialogModel(organizationModel);

  var usersTableConfig = {
      hideRoleFilter: true,
      hideEnabledFilter: true,
      ops: [{name: "removeFromOrg",
             showFor: _.partial(lupapisteApp.models.globalAuthModel.ok, "remove-user-organization"),
             operation: function(email, callback) {
               ajax
                 .command("remove-user-organization", {email: email})
                 .success(function() { callback(true); })
                 .call();
             }},
            {name: "editUser",
             showFor: _.partial(lupapisteApp.models.globalAuthModel.ok, "update-user-roles"),
             rowOperationFn: function (row) {
               editRolesDialogModel.showDialog({email: row[0], name: row[1], roles: row[2]});
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
    }
    statementGiversModel.load();
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

  $(function() {
    organizationModel.load();
    $("#applicationTabs").applyBindings({});
    $("#users").applyBindings({
      organizationUsers:   organizationUsers,
      statementGivers:    statementGiversModel,
      createStatementGiver: createStatementGiverModel,
      editRoles:           editRolesDialogModel
      });
    $("#applications").applyBindings({
      organization:        organizationModel,
      authorization:       lupapisteApp.models.globalAuthModel
    });
    $("#operations").applyBindings({
      organization:        organizationModel,
      editSelectedOperationsModel: editSelectedOperationsModel
    });
    $("#attachments").applyBindings({
      organization:        organizationModel,
      editAttachments:     editAttachmentsModel
    });
    $("#backends").applyBindings({
      organization:        organizationModel,
      wfs:                 wfsModel,
      kopiolaitos:         kopiolaitosModel,
      asianhallinta:       asianhallintaModel,
      linkToVendorBackend: linkToVendorBackendModel
    });
    $("#areas").applyBindings({
      organization:        organizationModel
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

    // Init the dynamically created dialogs
    LUPAPISTE.ModalDialog.init();
  });

})();
