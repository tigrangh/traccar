/*
 * Copyright 2015 - 2018 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.geolocation;

import org.traccar.Context;
import org.traccar.model.CellTower;
import org.traccar.model.Network;

import javax.json.JsonObject;
import javax.swing.text.html.parser.Entity;
import javax.ws.rs.client.InvocationCallback;

public class OpenCellIdGeolocationProvider implements GeolocationProvider {

    private String url;
    private Integer retry;

    public OpenCellIdGeolocationProvider(String url, String key) {
        this.url = key;
        retry = 0;
    }

    @Override
    public void getLocation(Network network, final LocationProviderCallback callback) {
        if (network.getCellTowers() != null && !network.getCellTowers().isEmpty()) {

            String cellids = new String();

            Integer mcc = new Integer(0), mnc = new Integer(0);

            for (CellTower cellTower : network.getCellTowers())
            {
                String cellid = String.format("{\"lac\":%d,\"cid\":%d}", cellTower.getLocationAreaCode(), cellTower.getCellId());

                if (cellids.isEmpty())
                    cellids += "[";
                else
                    cellids += ",";

                cellids += cellid;

                mcc = cellTower.getMobileCountryCode();
                mnc = cellTower.getMobileNetworkCode();
            }

            cellids += "]";

            String json = String.format("{\"token\":\"%s\",\"radio\":\"gsm\",\"mcc\":%d,\"mnc\":%d,\"cells\":%s,\"address\":1}", url, mcc, mnc, cellids);

            System.out.println(json);

            javax.ws.rs.client.Entity entity = javax.ws.rs.client.Entity.entity(json, javax.ws.rs.core.MediaType.APPLICATION_JSON);

            Context.getClient().target("https://us1.unwiredlabs.com/v2/process.php").request().async().post(
                entity,
                new InvocationCallback<JsonObject>() {
                @Override
                public void completed(JsonObject json) {

                    System.out.println(json);

                    if (json.containsKey("lat") && json.containsKey("lon")) {

                        retry = 0;

                        callback.onSuccess(
                                json.getJsonNumber("lat").doubleValue(),
                                json.getJsonNumber("lon").doubleValue(), 0);
                    } else {

                        if (retry < 10)
                        {
                            System.out.println("retry: " + retry);
                            getLocation(network, callback);
                        }
                        else
                        {
                            callback.onFailure(new GeolocationException("Coordinates are missing"));
                        }

                        ++retry;
                    }
                }

                @Override
                public void failed(Throwable throwable) {
                    callback.onFailure(throwable);
                    retry = 0;
                }
            });

        } else {
            callback.onFailure(new GeolocationException("No network information"));
        }
    }

}
