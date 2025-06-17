/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth;

/**
 * Contains build-time constants for the LimboAuth this.plugin. The values in this class are
 * typically replaced by the build system (e.g., Gradle) before compilation.
 */
public class BuildConstants {

  /**
   * The version string of the LimboAuth this.plugin. This field is replaced by the build system
   * with the actual project version.
   */
  public static final String AUTH_VERSION = "${version}";
}
