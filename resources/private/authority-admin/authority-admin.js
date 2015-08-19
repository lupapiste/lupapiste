(function() {
  "use strict";

  var organizationModel,
      organizationUsers,
      editSelectedOperationsModel,
      editAttachmentsModel,
      editLinkModel,
      wfsModel,
      statementGiversModel,
      createStatementGiverModel,
      kopiolaitosModel,
      asianhallintaModel,
      linkToVendorBackendModel,
      usersList = null,
      editRolesDialogModel;

  function OrganizationModel() {
    var self = this;

    self.organizationId = ko.observable();
    self.links = ko.observableArray();
    self.operationsAttachments = ko.observableArray();
    self.attachmentTypes = {};
    self.selectedOperations = ko.observableArray();
    self.allOperations = [];
    self.appRequiredFieldsFillingObligatory = ko.observable(false);
    self.tosFunctions = ko.observableArray();
    self.tosFunctionVisible = ko.observable(false);
    self.permanentArchiveEnabled = ko.observable(true);
    self.features = ko.observable();
    self.allowedRoles = ko.observable([]);

    self.load = function() { ajax.query("organization-by-user").success(self.init).call(); };

    self.appRequiredFieldsFillingObligatory.subscribe( function() {
      ajax
        .command("set-organization-app-required-fields-filling-obligatory", {isObligatory: self.appRequiredFieldsFillingObligatory()})
        .success(self.load)
        .call();
    });

    function toAttachments(attachments) {
      return _(attachments || [])
        .map(function(a) { return {id: a, text: loc(["attachmentType", a[0], a[1]])}; })
        .sortBy("text")
        .value();
    }

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

      self.permanentArchiveEnabled(organization["permanent-archive-enabled"] || false);

      // Operation attachments
      //
      var operationsAttachmentsPerPermitType = organization.operationsAttachments || {};
      var localizedOperationsAttachmentsPerPermitType = [];
      self.links(organization.links || []);

      var operationsTosFunctions = organization["operations-tos-functions"] || {};

      var setTosFunctionForOperation = function(operationId, functionCode) {
        ajax
          .command("set-tos-function-for-operation", {operation: operationId, functionCode: functionCode})
          .success(self.load)
          .call();
      };

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

      ajax
        .query("available-tos-functions", {organizationId: organization.id})
        .success(function(data) {
          self.tosFunctions(data.functions);
          if (data.functions.length > 0 && organization["permanent-archive-enabled"]) {
            self.tosFunctionVisible(true);
          }
        })
        .call();

      self.features(util.getIn(organization, ["areas"]));

      self.allowedRoles(organization.allowedRoles);
    };

    self.editLink = function(indexFn) {
      var index = indexFn();
      editLinkModel.init({
        source: this,
        commandName: "edit",
        command: function(url, nameFi, nameSv) {
          ajax
            .command("update-organization-link", {index: index, url: url, nameFi: nameFi, nameSv: nameSv})
            .success(self.load)
            .complete(LUPAPISTE.ModalDialog.close)
            .call();
        }
      });
      self.openLinkDialog();
    };

    self.addLink = function() {
      editLinkModel.init({
        commandName: "add",
        command: function(url, nameFi, nameSv) {
          ajax
            .command("add-organization-link", {url: url, nameFi: nameFi, nameSv: nameSv})
            .success(self.load)
            .complete(LUPAPISTE.ModalDialog.close)
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
  }

  function toAttachmentData(groupId, attachmentId) {
    return {
      id:   {"type-group": groupId, "type-id": attachmentId},
      text: loc(["attachmentType", groupId, attachmentId])
    };
  }

  function getAllOrganizationAttachmentTypes(permitType) {
    return _.map(organizationModel.attachmentTypes[permitType], function(g) {
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
          getAllOrganizationAttachmentTypes(op.permitType),
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
      self.nameFi(params.source ? params.source.name.fi : "");
      self.nameSv(params.source ? params.source.name.sv : "");
      self.url(params.source ? params.source.url : "");
    };

    self.execute = function() {
      self.command(self.url(), self.nameFi(), self.nameSv());
    };

    self.ok = ko.computed(function() {
      return self.nameFi() && self.nameFi().length > 0 && self.nameSv() && self.nameSv().length > 0 && self.url() && self.url().length > 0;
    });
  }

  function WFSModel() {
    var self = this;

    self.data = ko.observable();
    self.editUrl = ko.observable();
    self.versions = ko.observable([]);
    self.editVersion = ko.observable();
    self.editContext = null;
    self.error = ko.observable(false);

    self.load = function() {
      ajax.query("krysp-config")
        .success(function(d) {
          var data = d.krysp || [];
          // change map into a list where map key is one of the element keys
          // for easier handling with knockout
          self.data(_.map(_.keys(data), function(k) {
            var conf = data[k];
            conf.permitType = k;
            return conf;
          }));
        })
        .call();
    };

    self.save = function() {
      ajax.command("set-krysp-endpoint", {url: self.editUrl(), version: self.editVersion(), permitType: self.editContext.permitType})
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

    self.openDialog = function(model) {
      var url = model.url || "";
      var version = model.version || "";
      var versionsAvailable = LUPAPISTE.config.kryspVersions[model.permitType];
      if (!versionsAvailable) {error("No supported KRYSP versions for permit type", model.permitType);}
      self.versions(versionsAvailable);
      self.editUrl(url);
      self.editVersion(version);
      self.editContext = model;
      self.error(false);
      LUPAPISTE.ModalDialog.open("#dialog-edit-wfs");
    };

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
        .success(function() {
          self.indicator("asianhallinta");
        }).error(function() {
          self.indicator({name: "asianhallinta", type: "err"});
        })
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

  organizationModel = new OrganizationModel();
  organizationUsers = new LUPAPISTE.OrganizationUserModel(organizationModel);
  editSelectedOperationsModel = new EditSelectedOperationsModel();
  editAttachmentsModel = new EditAttachmentsModel();
  editLinkModel = new EditLinkModel();
  wfsModel = new WFSModel();
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

  hub.onPageLoad("admin", function() {
    if (!usersList) {
      usersList = users.create($("#admin .admin-users-table"), usersTableConfig);
    }
    organizationModel.load();
    wfsModel.load();
    statementGiversModel.load();
    kopiolaitosModel.load();
    asianhallintaModel.load();
    linkToVendorBackendModel.load();
  });

  $(function() {
    $("#admin").applyBindings({
      organizationUsers:   organizationUsers,
      organization:        organizationModel,
      editLink:            editLinkModel,
      editSelectedOperationsModel: editSelectedOperationsModel,
      editAttachments:     editAttachmentsModel,
      statementGivers:    statementGiversModel,
      createStatementGiver: createStatementGiverModel,
      wfs:                 wfsModel,
      kopiolaitos:         kopiolaitosModel,
      asianhallinta:       asianhallintaModel,
      linkToVendorBackend: linkToVendorBackendModel,
      editRoles:           editRolesDialogModel
    });
    // Init the dynamically created dialogs
    LUPAPISTE.ModalDialog.init();
  });

})();
