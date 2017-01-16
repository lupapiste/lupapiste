/**
* -- FOR DEVELOPMENT USE ONLY --
* Mimics API implemented by 3rd parties.
* Outputs data in modal dialog for debugging purposes.
* @constructs LupapisteApi
*/
function LupapisteApi() {

}

/**
* Show given permits on backend map (button in application list view).
* @static
* @param {Array<PermitFilter>} permits Permits from Lupapiste view
*/
LupapisteApi.showPermitsOnMap = function (permits) {
  "use strict";
  hub.send("show-dialog", {title: "LupapisteApi.showPermitsOnMap",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permits, null, 2)}});
};

/**
* Show point on backend map
* @static
* @param {PermitFilter} filter Filter for lupapiste api
*/
LupapisteApi.showPermitOnMap = function (permit) {
  "use strict";
  hub.send("show-dialog", {title: "LupapisteApi.showPermitOnMap",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};

/**
* Opens a permit in backend system.
* @static
* @param {PermitFilter} permit
*/
LupapisteApi.openPermit = function (permit) {
  "use strict";
  hub.send("show-dialog", {title: "LupapisteApi.openPermit",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};

/**
* Permit is emited when integration (KRYSP) message was created successfully.
* Backend may read the message and attachments from Lupapiste.
* @static
* @param {PermitFilter} permit
*/
LupapisteApi.integrationSent = function (permit) {
  "use strict";
  hub.send("show-dialog", {title: "LupapisteApi.integrationSent",
                           component: "ok-dialog",
                           componentParams: {text: JSON.stringify(permit, null, 2)}});
};
